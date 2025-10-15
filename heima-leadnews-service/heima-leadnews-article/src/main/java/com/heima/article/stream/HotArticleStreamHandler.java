package com.heima.article.stream;

import com.alibaba.fastjson.JSON;
import com.heima.common.constants.HotArticleConstants;
import com.heima.model.mess.ArticleVisitStreamMess;
import com.heima.model.mess.UpdateArticleMess;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@Slf4j
public class HotArticleStreamHandler {

    @Bean
    public KStream<String,String> kStream(StreamsBuilder streamsBuilder){
        // 从指定的 Kafka 主题中读取数据流（KStream）
        KStream<String,String> stream = streamsBuilder.stream(HotArticleConstants.HOT_ARTICLE_SCORE_TOPIC);

        // 对消息进行格式转换：把原始 JSON 转成 (文章ID, "类型:增量值") 的形式
        stream.map((key,value)->{
                    UpdateArticleMess mess = JSON.parseObject(value, UpdateArticleMess.class);
                    return new KeyValue<>(mess.getArticleId().toString(),mess.getType().name()+":"+mess.getAdd());
                })
                // 按照文章 ID 进行分组，方便后续做聚合
                .groupBy((key,value)->key)

                // 定义一个 10 秒的时间窗口，用于对同一文章在 10 秒内的消息做聚合计算
                .windowedBy(TimeWindows.of(Duration.ofSeconds(10)))

                // 定义聚合逻辑：先初始化，再累加
                .aggregate(new Initializer<String>() {
                    // 初始化：当某篇文章第一次进入窗口时，设置初始值为 0
                    @Override
                    public String apply() {
                        return "COLLECTION:0,COMMENT:0,LIKES:0,VIEWS:0";
                    }
                },new Aggregator<String, String, String>() {
                    // 聚合逻辑：窗口内每来一条消息都会执行一次
                    @Override
                    public String apply(String key, String value, String aggValue) {
                        if(StringUtils.isBlank(value)){
                            return aggValue; // 如果消息为空，就直接返回当前累积值
                        }

                        // 解析当前窗口的历史聚合结果（也就是上一次的累加状态）
                        String[] aggAry = aggValue.split(",");
                        int col=0,com=0,lik=0,vie=0;
                        for (String s : aggAry) {
                            String[] split = s.split(":");
                            // 从上次的聚合结果中恢复各项统计数据
                            switch (UpdateArticleMess.UpdateArticleType.valueOf(split[0])){
                                case COLLECTION:
                                    col = Integer.parseInt(split[1]);
                                    break;
                                case COMMENT:
                                    com = Integer.parseInt(split[1]);
                                    break;
                                case LIKES:
                                    lik = Integer.parseInt(split[1]);
                                    break;
                                case VIEWS:
                                    vie = Integer.parseInt(split[1]);
                                    break;
                            }
                        }

                        // 将当前这条消息对应的字段加到累计结果中
                        String[] valAry = value.split(":");
                        switch (UpdateArticleMess.UpdateArticleType.valueOf(valAry[0])){
                            case COLLECTION:
                                col += Integer.parseInt(valAry[1]);
                                break;
                            case COMMENT:
                                com += Integer.parseInt(valAry[1]);
                                break;
                            case LIKES:
                                lik += Integer.parseInt(valAry[1]);
                                break;
                            case VIEWS:
                                vie += Integer.parseInt(valAry[1]);
                                break;
                        }

                        // 拼接最新聚合结果，并打印日志
                        String formatStr = String.format("COLLECTION:%d,COMMENT:%d,LIKES:%d,VIEWS:%d", col, com, lik, vie);
                        System.out.println("文章的ID："+ key);
                        System.out.println("当前时间窗口内的消息处理结果："+formatStr);
                        return formatStr; // 返回新的聚合结果（进入状态存储）
                    }
                },Materialized.as("hot-article-stream-count-001")) // 定义状态存储名称

                // 把窗口聚合结果转为普通流（key类型从Windowed<String>还原为String）
                .toStream()
                .map((key,value)->{
                    // 将 key 转成文章ID字符串，同时格式化 value 为下游可识别的 JSON 对象
                    return new KeyValue<>(key.key().toString(),formatObj(key.key().toString(),value));
                })

                // 把聚合好的结果发送到另一个 Kafka 主题，供下游服务使用
                .to(HotArticleConstants.HOT_ARTICLE_INCR_HANDLE_TOPIC);

        // 返回定义的流对象
        return stream;
    }

    // 把聚合后的字符串格式转成对象再转JSON，方便下游解析
    private String formatObj(String articleId, String value) {
        ArticleVisitStreamMess mess = new ArticleVisitStreamMess();
        mess.setArticleId(Long.valueOf(articleId)); // 设置文章ID

        String[] valAry = value.split(","); // 拆分四种指标
        for (String val : valAry) {
            String[] split = val.split(":");
            // 根据字段类型设置对象属性
            switch (UpdateArticleMess.UpdateArticleType.valueOf(split[0])){
                case COLLECTION:
                    mess.setCollect(Integer.parseInt(split[1]));
                    break;
                case COMMENT:
                    mess.setComment(Integer.parseInt(split[1]));
                    break;
                case LIKES:
                    mess.setLike(Integer.parseInt(split[1]));
                    break;
                case VIEWS:
                    mess.setView(Integer.parseInt(split[1]));
                    break;
            }
        }
        log.info("处理之后的数据："+JSON.toJSONString(mess)); // 输出日志
        return JSON.toJSONString(mess); // 返回 JSON 字符串
    }
}
