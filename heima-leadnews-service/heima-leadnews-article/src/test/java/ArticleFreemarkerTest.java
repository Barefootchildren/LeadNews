import com.alibaba.fastjson.JSONArray;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.heima.article.ArticleApplication;
import com.heima.article.mapper.ApArticleContentMapper;
import com.heima.article.mapper.ApArticleMapper;
import com.heima.file.service.FileStorageService;
import com.heima.model.article.pojos.ApArticle;
import com.heima.model.article.pojos.ApArticleContent;
import freemarker.template.Configuration;
import freemarker.template.Template;
import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;


@SpringBootTest(classes = ArticleApplication.class)
@RunWith(SpringRunner.class)
public class ArticleFreemarkerTest {
    @Autowired
    private Configuration configuration;

    @Autowired
    private FileStorageService fileStorageService;


    @Autowired
    private ApArticleMapper apArticleMapper;

    @Autowired
    private ApArticleContentMapper apArticleContentMapper;

    @Test
    public void createStaticUrlTest()throws Exception{
        // 查询文章内容信息
        ApArticleContent apArticleContent = apArticleContentMapper.selectOne(Wrappers.<ApArticleContent>lambdaQuery()
                .eq(ApArticleContent::getArticleId, 1302862387124125698L));
        // 判断文章内容是否存在且不为空
        if(apArticleContent!=null&& StringUtils.isNotBlank(apArticleContent.getContent())){
            // 创建字符串输出流和模板对象
            StringWriter out = new StringWriter();
            Template template = configuration.getTemplate("article.ftl");
            // 准备模板参数，将文章内容解析为JSON数组
            Map<String,Object> params = new HashMap<>();
            params.put("content", JSONArray.parseArray(apArticleContent.getContent()));
            // 处理模板，生成HTML内容
            template.process(params,out);
            // 将生成的HTML内容转换为输入流
            InputStream is = new ByteArrayInputStream(out.toString().getBytes());
            // 上传HTML文件到文件存储服务
            String path = fileStorageService.uploadHtmlFile("", apArticleContent.getArticleId() + ".html", is);
            // 更新文章的静态URL路径
            ApArticle article = new ApArticle();
            article.setId(apArticleContent.getArticleId());
            article.setStaticUrl(path);
            apArticleMapper.updateById(article);

        }
    }
}
