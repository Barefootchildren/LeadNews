package com.heima.schedule.service.impl;

import com.alibaba.fastjson.JSON;
import com.heima.common.constants.ScheduleConstants;
import com.heima.common.redis.CacheService;
import com.heima.model.schedule.dtos.Task;
import com.heima.model.schedule.pojos.Taskinfo;
import com.heima.model.schedule.pojos.TaskinfoLogs;
import com.heima.schedule.mapper.TaskinfoLogsMapper;
import com.heima.schedule.mapper.TaskinfoMapper;
import com.heima.schedule.service.TaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Calendar;
import java.util.Date;

@Service
@Transactional
@Slf4j
public class TaskServiceImpl implements TaskService {
    /**
     * 添加任务到系统中
     * 首先将任务添加到数据库，如果添加成功则同时添加到缓存中
     *
     * @param task 要添加的任务对象，不能为null
     * @return 返回添加的任务的唯一标识符
     */
    @Override
    public long addTask(Task task) {
        // 将任务添加到数据库
        boolean b = addTaskToDb(task);
        if(b){
            // 数据库添加成功后，将任务添加到缓存
            addTaskToCache(task);
        }
        return task.getTaskId();
    }

    @Resource
    private CacheService cacheService;
    /**
     * 将任务添加到缓存中
     * 根据任务的执行时间和优先级，将任务存储到不同的缓存队列中
     * @param task 需要添加到缓存的任务对象
     */
    private void addTaskToCache(Task task) {
        String key = task.getTaskType() + "_" + task.getPriority();

        // 计算5分钟后的调度时间
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE,5);
        long nextScheduleTime = calendar.getTimeInMillis();

        // 根据任务执行时间判断存储方式
        if(task.getExecuteTime()<=System.currentTimeMillis()){
            // 立即执行的任务存储到列表左侧
            cacheService.lLeftPush(ScheduleConstants.TOPIC+key, JSON.toJSONString(task));
        } else if (task.getExecuteTime() <= nextScheduleTime) {
            // 5分钟内需要执行的任务存储到有序集合中
            cacheService.zAdd(ScheduleConstants.FUTURE+key,JSON.toJSONString(task),task.getExecuteTime());
        }
    }

    @Resource
    private TaskinfoMapper taskinfoMapper;

    @Resource
    private TaskinfoLogsMapper taskinfoLogsMapper;
    /**
     * 将任务信息添加到数据库中
     * @param task 需要添加的任务对象
     * @return 添加成功返回true，失败返回false
     */
    private boolean addTaskToDb(Task task) {
        boolean flag=false;
        try {
            // 创建任务信息对象并复制属性
            // 创建任务信息对象
            Taskinfo taskinfo = new Taskinfo();
            // 复制任务属性到任务信息对象
            BeanUtils.copyProperties(task,taskinfo);
            // 设置执行时间
            taskinfo.setExecuteTime(new Date(task.getExecuteTime()));
            // 插入任务信息到数据库
            taskinfoMapper.insert(taskinfo);
            // 将生成的任务ID设置回原任务对象
            task.setTaskId(taskinfo.getTaskId());


            // 创建任务信息日志对象并设置初始状态
            // 创建任务日志对象并复制任务信息属性
            TaskinfoLogs taskinfoLogs = new TaskinfoLogs();
            BeanUtils.copyProperties(taskinfo,taskinfoLogs);

            // 设置任务日志的初始版本号和状态
            taskinfoLogs.setVersion(1);
            taskinfoLogs.setStatus(ScheduleConstants.SCHEDULED);

            // 插入任务日志记录
            taskinfoLogsMapper.insert(taskinfoLogs);
            flag=true;

        } catch (Exception e) {
            e.printStackTrace();
        }
        return flag;
    }

}
