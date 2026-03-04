package com.nori.tc.db.starter.redis;

import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis Starter ?먮룞 援ъ꽦?낅땲??
 *
 * <p>??븷</p>
 * <p>1) Redis 愿??湲곕낯 Bean(RedisConnectionFactory, RedisTemplate)? Spring Boot 湲곕낯 ?먮룞 援ъ꽦???꾩엫?⑸땲??</p>
 * <p>2) 蹂?starter媛 怨듯넻?쇰줈 ?ъ슜??{@code tcRedisTemplate}, {@code TcRedisCrudRepository}瑜?異붽?濡?援ъ꽦?⑸땲??</p>
 * <p>3) 愿怨꾪삎 DB starter? ?④퍡 ?ъ슜?????덈룄濡?Redis ?꾩슜 諛고? ??Bean???깅줉?⑸땲??</p>
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration",
        "org.springframework.boot.data.redis.autoconfigure.LettuceConnectionConfiguration"
})
public class TcDbRedisAutoConfiguration {

    /**
     * Redis starter ?꾩슜 諛고? ??Bean?낅땲??
     *
     * <p>愿怨꾪삎 DB starter?먯꽌 ?ъ슜?섎뒗 ??Bean ?대쫫({@code tcDbStarterExclusiveLock})怨?遺꾨━?댁꽌,
     * Redis starter? 愿怨꾪삎 DB starter瑜??④퍡 ?ъ슜????Bean ?대쫫 異⑸룎??諛⑹??⑸땲??</p>
     */
    @Bean(name = "tcDbRedisStarterExclusiveLock")
    public Object tcDbRedisStarterExclusiveLock() {
        return "tc-db-redis-starter";
    }

    /**
     * 怨듯넻 RedisTemplate({@code tcRedisTemplate})???깅줉?⑸땲??
     *
     * <p>議곌굔</p>
     * <p>- RedisConnectionFactory媛 議댁옱???뚮쭔 ?앹꽦?⑸땲??</p>
     * <p>- ?숈씪 ?대쫫??Bean???대? ?덉쑝硫?以묐났 ?앹꽦?섏? ?딆뒿?덈떎.</p>
     *
     * @param connectionFactory Redis ?곌껐 ?⑺넗由?     * @param valueSerializerProvider 媛?吏곷젹?붽린 ?쒓났??誘몄?????JDK 吏곷젹?붽린 ?ъ슜)
     * @return 怨듯넻 RedisTemplate
     */
    @Bean(name = "tcRedisTemplate")
    @ConditionalOnBean(RedisConnectionFactory.class)
    @ConditionalOnMissingBean(name = "tcRedisTemplate")
    public RedisTemplate<String, Object> tcRedisTemplate(
            final RedisConnectionFactory connectionFactory,
            final ObjectProvider<RedisSerializer<Object>> valueSerializerProvider
    ) {
        final RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        final RedisSerializer<String> keySerializer = new StringRedisSerializer();
        final RedisSerializer<Object> valueSerializer = Optional.ofNullable(valueSerializerProvider.getIfAvailable())
                .orElseGet(GenericJackson2JsonRedisSerializer::new);

        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        template.afterPropertiesSet();
        return template;
    }

    /**
     * TcRedisCrudRepository 援ы쁽泥대? ?깅줉?⑸땲??
     *
     * <p>議곌굔</p>
     * <p>- {@code tcRedisTemplate} Bean??議댁옱???뚮쭔 ?앹꽦?⑸땲??</p>
     * <p>- ?대? 援ы쁽泥닿? ?깅줉?섏뼱 ?덉쑝硫?以묐났 ?앹꽦?섏? ?딆뒿?덈떎.</p>
     *
     * @param tcRedisTemplate 怨듯넻 RedisTemplate
     * @return Redis CRUD ??μ냼 援ы쁽泥?     */
    @Bean
    @ConditionalOnBean(name = "tcRedisTemplate")
    @ConditionalOnMissingBean
    public TcRedisCrudRepository tcRedisCrudRepository(final RedisTemplate<String, Object> tcRedisTemplate) {
        return new TcRedisTemplateCrudRepository(tcRedisTemplate);
    }
}
