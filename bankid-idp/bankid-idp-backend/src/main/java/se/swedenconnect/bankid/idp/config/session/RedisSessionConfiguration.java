/*
 * Copyright 2023 Sweden Connect
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package se.swedenconnect.bankid.idp.config.session;

import org.redisson.api.RedissonClient;
import org.redisson.config.SingleServerConfig;
import org.redisson.spring.starter.RedissonAutoConfiguration;
import org.redisson.spring.starter.RedissonAutoConfigurationCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import se.swedenconnect.bankid.idp.authn.session.RedisSessionDao;
import se.swedenconnect.bankid.idp.authn.session.SessionDao;
import se.swedenconnect.bankid.idp.concurrency.TryLockRepository;
import se.swedenconnect.bankid.idp.concurrency.RedisTryLockRepository;
import se.swedenconnect.bankid.idp.config.RedisSecurityProperties;
import se.swedenconnect.bankid.idp.ext.RedisReplayChecker;

import java.io.IOException;

@Configuration
@ConditionalOnProperty(value = "session.module", havingValue = "redis")
@Import({RedissonAutoConfiguration.class, RedisAutoConfiguration.class})
@EnableRedisHttpSession
public class RedisSessionConfiguration {
  private final ResourceLoader loader = new DefaultResourceLoader();

  @Bean
  @ConfigurationProperties(prefix = "spring.redis.tls")
  public RedisSecurityProperties redisSecurityProperties() {
    return new RedisSecurityProperties();
  }
  @Bean
  public RedissonAutoConfigurationCustomizer sslCustomizer(final RedisSecurityProperties properties) {
    final Resource keystore = loader.getResource(properties.getP12KeyStorePath());
    return c -> {
      try {
        final SingleServerConfig singleServerConfig = c.useSingleServer()
            .setSslKeystore(keystore.getURL())
            .setSslKeystorePassword(properties.getP12KeyStorePassword());
        singleServerConfig.setSslEnableEndpointIdentification(properties.getEnableHostnameVerification());
        if (properties.getEnableHostnameVerification()) {
          final Resource truststore = loader.getResource(properties.getP12TrustStorePath());
          singleServerConfig
              .setSslTruststore(truststore.getURL())
              .setSslTruststorePassword(properties.getP12TrustStorePassword());
        }
      } catch (final IOException e) {
        throw new RuntimeException(e);
      }
    };
  }

  @Bean
  public TryLockRepository repository(final RedissonClient client) {
    return new RedisTryLockRepository(client);
  }

  @Bean
  public SessionDao redisSessionDao(final RedissonClient client) {
    return new RedisSessionDao(client);
  }

  @Bean
  public RedisReplayChecker redisReplayChecker(final RedissonClient client) {
    return new RedisReplayChecker(client);
  }
}
