import com.heima.freemarker.FreemarkerDemoApplication;
import com.heima.freemarker.entity.Student;
import freemarker.template.Configuration;
import freemarker.template.Template;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@SpringBootTest(classes = FreemarkerDemoApplication.class)
@RunWith(SpringRunner.class)
public class FreemarkerTest {
    @Autowired
    private Configuration configuration;
    @Test
    public void test()throws Exception{
        Template template = configuration.getTemplate("02-list.ftl");
        Map params=getData();
        template.process(params,new FileWriter("C:/Java/WorkSpace/heima-leadnews/list.html"));
    }

    private Map getData() {
        HashMap<String, Object> map = new HashMap<>();
        Student student = new Student();
        student.setName("张三");
        student.setAge(18);
        student.setMoney(1000.86f);
        student.setBirthday(new Date());
        Student student2 = new Student();
        student2.setName("李四");
        student2.setMoney(200.1f);
        student2.setAge(28);
        student2.setBirthday(new Date());
        ArrayList<Student> students = new ArrayList<>();
        students.add(student);
        students.add(student2);
        map.put("stus",students);
        HashMap<String,Student> stuMap = new HashMap<>();
        stuMap.put("stu1",student);
        stuMap.put("stu2",student2);
        map.put("stuMap",stuMap);
        //map.put("today",new Date());
        return map;
    }
}
