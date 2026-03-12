package cn.yy.myrent.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.convert.ElasticsearchConverter;

/**
 * Exposes the ElasticsearchOperations bean backed by the Elastic Java API client.
 * Spring Boot auto-configures ElasticsearchClient from spring.elasticsearch.* properties.
 */
@Configuration
public class ElasticsearchConfig {

    @Bean
    public ElasticsearchOperations elasticsearchOperations(ElasticsearchClient client,
                                                           ElasticsearchConverter converter) {
        return new ElasticsearchTemplate(client, converter);
    }
}
