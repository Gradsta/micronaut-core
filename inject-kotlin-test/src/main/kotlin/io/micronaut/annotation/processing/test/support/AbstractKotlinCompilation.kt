/*
 * Copyright 2017-2023 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.annotation.processing.test.support

import okio.Buffer
import org.jetbrains.kotlin.cli.common.CLICompiler
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.parseCommandLineArguments
import org.jetbrains.kotlin.cli.common.arguments.validateArguments
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlin.load.java.JvmAbi
import org.jetbrains.kotlin.util.ServiceLoaderLite
import java.io.File
import java.io.OutputStream
import java.io.PrintStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Base compilation class for sharing common compiler arguments and
 * functionality. Should not be used outside of this library as it is an
 * implementation detail.
 */
abstract class AbstractKotlinCompilation<A : CommonCompilerArguments> internal constructor() {
    /** Working directory for the compilation */
    var workingDir: File by default {
        val path = Files.createTempDirectory("Kotlin-Compilation")
        log("Created temporary working directory at ${path.toAbsolutePath()}")
        return@default path.toFile()
    }

    /**
     * Paths to directories or .jar files that contain classes
     * to be made available in the compilation (i.e. added to
     * the classpath)
     */
    var classpaths: List<File> = emptyList()

    /**
     * Paths to plugins to be made available in the compilation
     */
    var pluginClasspaths: List<File> = emptyList()

    /**
     * Compiler plugins that should be added to the compilation
     */
    @OptIn(ExperimentalCompilerApi::class)
    var compilerPlugins: List<ComponentRegistrar> = emptyList()

    /**
     * Commandline processors for compiler plugins that should be added to the compilation
     */
    @OptIn(ExperimentalCompilerApi::class)
    var commandLineProcessors: List<CommandLineProcessor> = emptyList()

    /** Source files to be compiled */
    var sources: List<SourceFile> = emptyList()

    /** Print verbose logging info */
    var verbose: Boolean = true

    /**
     * Helpful information (if [verbose] = true) and the compiler
     * system output will be written to this stream
     */
    var messageOutputStream: OutputStream = System.out

    /** Inherit classpath from calling process */
    var inheritClassPath: Boolean = false

    /** Suppress all warnings */
    var suppressWarnings: Boolean = false

    /** All warnings should be treated as errors */
    var allWarningsAsErrors: Boolean = false

    /** Report locations of files generated by the compiler */
    var reportOutputFiles: Boolean by default { verbose }

    /** Report on performance of the compilation */
    var reportPerformance: Boolean = false

    var languageVersion: String? = null

    /** Use the new experimental K2 compiler */
    var useK2: Boolean by default { false }

    /** Enable experimental multiplatform support */
    var multiplatform: Boolean = false

    /** Do not check presence of 'actual' modifier in multi-platform projects */
    var noCheckActual: Boolean = false

    /** Enable usages of API that requires opt-in with an opt-in requirement marker with the given fully qualified name */
    var optIn: List<String>? = null

    /** Additional string arguments to the Kotlin compiler */
    var kotlincArguments: List<String> = emptyList()

    /** Options to be passed to compiler plugins: -P plugin:<pluginId>:<optionName>=<value>*/
    var pluginOptions: List<PluginOption> = emptyList()

    /**
     * Path to the kotlin-stdlib-common.jar
     * If none is given, it will be searched for in the host
     * process' classpaths
     */
    var kotlinStdLibCommonJar: File? by default {
        HostEnvironment.kotlinStdLibCommonJar
    }

    // Directory for input source files
    protected val sourcesDir get() = workingDir.resolve("sources")

    protected inline fun <reified T> CommonCompilerArguments.trySetDeprecatedOption(optionSimpleName: String, value: T) {
        try {
            this.javaClass.getMethod(JvmAbi.setterName(optionSimpleName), T::class.java).invoke(this, value)
        } catch (e: ReflectiveOperationException) {
            throw IllegalArgumentException(
                "The deprecated option $optionSimpleName is no longer available in the kotlin version you are using",
                e
            )
        }
    }

    protected fun commonArguments(args: A, configuration: (args: A) -> Unit): A {
        args.pluginClasspaths = pluginClasspaths.map(File::getAbsolutePath).toTypedArray()

        args.verbose = verbose

        args.suppressWarnings = suppressWarnings
        args.allWarningsAsErrors = allWarningsAsErrors
        args.reportOutputFiles = reportOutputFiles
        args.reportPerf = reportPerformance
        args.useK2 = useK2
        args.multiPlatform = multiplatform
        args.noCheckActual = noCheckActual
        args.optIn = optIn?.toTypedArray()

        if (languageVersion != null)
            args.languageVersion = this.languageVersion

        configuration(args)


        /* Parse extra CLI arguments that are given as strings so users can specify arguments that are not yet
        implemented here as well-typed properties. */
        parseCommandLineArguments(kotlincArguments, args)

        validateArguments(args.errors)?.let {
            throw IllegalArgumentException("Errors parsing kotlinc CLI arguments:\n$it")
        }

        return args
    }

    /** Performs the compilation step to compile Kotlin source files */
    @OptIn(ExperimentalCompilerApi::class)
    protected fun compileKotlin(sources: List<File>, compiler: CLICompiler<A>, arguments: A): KotlinCompilation.ExitCode {

        /**
         * Here the list of compiler plugins is set
         *
         * To avoid that the annotation processors are executed twice,
         * the list is set to empty
         */
        MainComponentRegistrar.threadLocalParameters.set(
            MainComponentRegistrar.ThreadLocalParameters(
                compilerPlugins
            )
        )

        // in this step also include source files generated by kapt in the previous step
        val args = arguments.also { args ->
            args.freeArgs =
                sources.map(File::getAbsolutePath).distinct() + if (sources.none(File::hasKotlinFileExtension)) {
                    /* __HACK__: The Kotlin compiler expects at least one Kotlin source file or it will crash,
                       so we trick the compiler by just including an empty .kt-File. We need the compiler to run
                       even if there are no Kotlin files because some compiler plugins may also process Java files. */
                    listOf(SourceFile.new("emptyKotlinFile.kt", "").writeIfNeeded(sourcesDir).absolutePath)
                } else {
                    emptyList()
                }
            args.pluginClasspaths = (args.pluginClasspaths ?: emptyArray()) +
                    /** The resources path contains the MainComponentRegistrar and MainCommandLineProcessor which will
                     be found by the Kotlin compiler's service loader. We add it only when the user has actually given
                     us ComponentRegistrar instances to be loaded by the MainComponentRegistrar because the experimental
                     K2 compiler doesn't support plugins yet. This way, users of K2 can prevent MainComponentRegistrar
                     from being loaded and crashing K2 by setting both [compilerPlugins] and [commandLineProcessors] to
                     the emptyList. */
                    if (compilerPlugins.isNotEmpty() || commandLineProcessors.isNotEmpty())
                        arrayOf(getResourcesPath())
                    else emptyArray()
        }

        val compilerMessageCollector = PrintingMessageCollector(
            internalMessageStream, MessageRenderer.GRADLE_STYLE, verbose
        )

        return convertKotlinExitCode(
            compiler.exec(compilerMessageCollector, Services.EMPTY, args)
        )
    }

    @OptIn(ExperimentalCompilerApi::class)
    protected fun getResourcesPath(): String {
        val resourceName = "META-INF/services/org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar"
        return this::class.java.classLoader.getResources(resourceName)
            .asSequence()
            .mapNotNull { url ->
                val uri = URI.create(url.toString().removeSuffix("/$resourceName"))
                when (uri.scheme) {
                    "jar" -> Paths.get(URI.create(uri.schemeSpecificPart.removeSuffix("!")))
                    "file" -> Paths.get(uri)
                    else -> return@mapNotNull null
                }.toAbsolutePath()
            }
            .find { resourcesPath ->
                ServiceLoaderLite.findImplementations(ComponentRegistrar::class.java, listOf(resourcesPath.toFile()))
                    .any { implementation -> implementation == MainComponentRegistrar::class.java.name }
            }?.toString() ?: throw AssertionError("Could not get path to ComponentRegistrar service from META-INF")
    }

    /** Searches compiler log for known errors that are hard to debug for the user */
    protected fun searchSystemOutForKnownErrors(compilerSystemOut: String) {
        if (compilerSystemOut.contains("No enum constant com.sun.tools.javac.main.Option.BOOT_CLASS_PATH")) {
            warn(
                "${this::class.simpleName} has detected that the compiler output contains an error message that may be " +
                        "caused by including a tools.jar file together with a JDK of version 9 or later. " +
                        if (inheritClassPath)
                            "Make sure that no tools.jar (or unwanted JDK) is in the inherited classpath"
                        else ""
            )
        }

        if (compilerSystemOut.contains("Unable to find package java.")) {
            warn(
                "${this::class.simpleName} has detected that the compiler output contains an error message " +
                        "that may be caused by a missing JDK. This can happen if jdkHome=null and inheritClassPath=false."
            )
        }
    }

    protected val hostClasspaths by lazy { HostEnvironment.classpath }

    /* This internal buffer and stream is used so it can be easily converted to a string
    that is put into the [Result] object, in addition to printing immediately to the user's
    stream. */
    protected val internalMessageBuffer = Buffer()
    protected val internalMessageStream = PrintStream(
        TeeOutputStream(
            object : OutputStream() {
                override fun write(b: Int) = messageOutputStream.write(b)
                override fun write(b: ByteArray) = messageOutputStream.write(b)
                override fun write(b: ByteArray, off: Int, len: Int) = messageOutputStream.write(b, off, len)
                override fun flush() = messageOutputStream.flush()
                override fun close() = messageOutputStream.close()
            },
            internalMessageBuffer.outputStream()
        )
    )

    protected fun log(s: String) {
        if (verbose)
            internalMessageStream.println("logging: $s")
    }

    protected fun warn(s: String) = internalMessageStream.println("warning: $s")
    protected fun error(s: String) = internalMessageStream.println("error: $s")

    internal val internalMessageStreamAccess: PrintStream get() = internalMessageStream
}

internal fun convertKotlinExitCode(code: ExitCode) = when(code) {
    ExitCode.OK -> KotlinCompilation.ExitCode.OK
    ExitCode.OOM_ERROR,
    ExitCode.INTERNAL_ERROR -> KotlinCompilation.ExitCode.INTERNAL_ERROR
    ExitCode.COMPILATION_ERROR -> KotlinCompilation.ExitCode.COMPILATION_ERROR
    ExitCode.SCRIPT_EXECUTION_ERROR -> KotlinCompilation.ExitCode.SCRIPT_EXECUTION_ERROR
    ExitCode.OOM_ERROR -> throw OutOfMemoryError("Kotlin compiler ran out of memory")
}
