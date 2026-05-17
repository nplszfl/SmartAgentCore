package com.agent.config;

import com.mybatisflex.spring.FlexSqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
@MapperScan("com.agent.mapper")
public class MyBatisFlexConfig {

    @Autowired
    private DataSource dataSource;

    @Bean
    public FlexSqlSessionFactoryBean sqlSessionFactory() throws Exception {
        FlexSqlSessionFactoryBean sessionFactory = new FlexSqlSessionFactoryBean();
        sessionFactory.setDataSource(dataSource);
        return sessionFactory;
    }
}
