package com.heima.freemarker.controller;

import com.heima.freemarker.entity.Student;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

@Controller
public class HelloController {
    @GetMapping("basic")
    public String test(Model model){
        model.addAttribute("name","freemarker");
        Student student = new Student();
        student.setName("张三");
        student.setAge(18);
        model.addAttribute("stu",student);
        return "01-basic";
    }
    @GetMapping("list")
    public String list(Model model){
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
        model.addAttribute("stus",students);
        HashMap<String,Student> map = new HashMap<>();
        map.put("stu1",student);
        map.put("stu2",student2);
        model.addAttribute("stuMap",map);
        //model.addAttribute("exit","（我传过来了）");
        model.addAttribute("exit2","存在");
        model.addAttribute("today",new Date());
        return "02-list";
    }
}
