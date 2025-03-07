package io.micronaut.aop.compile

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.InstantiatableBeanDefinition
import io.micronaut.inject.writer.BeanDefinitionWriter

class PropertyAdviceSpec extends AbstractBeanDefinitionSpec {
    void 'test advice can be applied to bean properties'() {
        when:"An introduction advice type is compiled that includes a concrete method that is annotated with around advice"
        BeanDefinition beanDefinition = buildBeanDefinition('test.$MyPropertyBean' + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionWriter.PROXY_SUFFIX, '''
package test;

import io.micronaut.aop.interceptors.*;
import io.micronaut.context.annotation.*;
import javax.validation.constraints.*;
import javax.inject.Singleton;

@Mutating("name")
@javax.inject.Singleton
class MyPropertyBean {
    String name

    void test(String name) {}
}

''')

        then:"The around advice is applied to the concrete method"
        beanDefinition != null

        when:
        ApplicationContext context = ApplicationContext.run()
        def instance = ((InstantiatableBeanDefinition) beanDefinition).instantiate(context)
        instance.setName("test")

        then:
        instance.name == 'changed'

        cleanup:
        context.close()
    }
}
