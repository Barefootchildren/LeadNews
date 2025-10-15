package com.heima.article.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class XxlJobConfig {
    private Logger logger = LoggerFactory.getLogger(XxlJobConfig.class);

    @Value("${xxl.job.admin.addresses}")
    private String adminAddresses;

    @Value("${xxl.job.executor.appname}")
    private String appname;

    @Value("${xxl.job.executor.port}")
    private int port;
    /**
     * 初始化并配置XXL-JOB执行器Bean
     *
     * @return 配置完成的XxlJobSpringExecutor实例
     */
    @Bean
    public XxlJobSpringExecutor xxlJobExecutor() {
        logger.info(">>>>>>>>>>> xxl-job config init.");
        // 创建XXL-JOB Spring执行器实例
        XxlJobSpringExecutor xxlJobSpringExecutor = new XxlJobSpringExecutor();
        // 设置调度中心地址
        xxlJobSpringExecutor.setAdminAddresses(adminAddresses);
        // 设置执行器应用名称
        xxlJobSpringExecutor.setAppname(appname);
        // 设置执行器端口
        xxlJobSpringExecutor.setPort(port);
        return xxlJobSpringExecutor;
    }

}
