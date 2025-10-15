package com.heima.schedule.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.heima.common.constants.ScheduleConstants;
import com.heima.common.redis.CacheService;
import com.heima.model.schedule.dtos.Task;
import com.heima.model.schedule.pojos.Taskinfo;
import com.heima.model.schedule.pojos.TaskinfoLogs;
import com.heima.schedule.mapper.TaskinfoLogsMapper;
import com.heima.schedule.mapper.TaskinfoMapper;
import com.heima.schedule.service.TaskService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.RedisConnectionUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;

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

    /**
     * 取消指定的任务
     *
     * @param taskId 任务ID
     * @return 取消成功返回true，否则返回false
     */
    @Override
    public boolean cancelTask(long taskId) {
        boolean flag = false;
        // 更新数据库中的任务状态为已执行
        Task task=updateDb(taskId,ScheduleConstants.CANCELLED);
        if(task!=null){
            // 从缓存中移除任务
            removeTaskFromCache(task);
            flag=true;
        }
        return flag;
    }

    /**
     * 从缓存中获取指定类型和优先级的任务
     * @param type 任务类型
     * @param priority 任务优先级
     * @return 返回获取到的任务对象，如果获取失败或无任务则返回null
     */
    @Override
    public Task poll(int type, int priority) {
        Task task=null;
        try {
            // 构造缓存key并从缓存右侧弹出任务数据
            String key = type + "_" + priority;
            String task_json = cacheService.lRightPop(ScheduleConstants.TOPIC + key);
            if(StringUtils.isNotBlank(task_json)){
                // 将json字符串转换为Task对象
                task = JSON.parseObject(task_json, Task.class);
                // 更新数据库中任务状态为已执行
                updateDb(task.getTaskId(),ScheduleConstants.EXECUTED);
            }
        }catch (Exception e){
            e.printStackTrace();
            log.error("poll task exception");
        }
        return task;
    }
    /**
     * 重新加载数据到缓存中
     * 此方法会在以下两个时机被调用：
     * 1. 应用启动时（通过@PostConstruct注解）
     * 2. 每5分钟定时执行一次
     * 方法主要功能：
     * 1. 清空现有缓存
     * 2. 从数据库查询5分钟内需要执行的任务
     * 3. 将查询到的任务数据转换并添加到缓存中
     */
    @Scheduled(cron = "0 */5 * * * ?")
    @PostConstruct
    public void reloadData() {
        // 清空缓存数据
        clearCache();
        log.info("数据库数据同步到缓存");

        // 计算5分钟后的时间，用于查询即将执行的任务
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE,5);

        // 查询execute_time小于5分钟后时间的所有任务
        List<Taskinfo> allTasks = taskinfoMapper.selectList(Wrappers.<Taskinfo>lambdaQuery()
                .lt(Taskinfo::getExecuteTime, calendar.getTime()));

        // 将查询到的任务信息转换为Task对象并添加到缓存
        if(allTasks!=null&&allTasks.size()>0){
            for (Taskinfo taskinfo : allTasks){
                Task task = new Task();
                BeanUtils.copyProperties(taskinfo,task);
                task.setExecuteTime(taskinfo.getExecuteTime().getTime());
                addTaskToCache(task);
            }
        }
    }

    /**
     * 清除缓存中的定时任务相关数据
     * 该方法会扫描并删除所有以ScheduleConstants.FUTURE和ScheduleConstants.TOPIC为前缀的缓存键
     */
    private void clearCache(){
        // 扫描所有future相关的缓存键
        Set<String> futureKeys = cacheService.scan(ScheduleConstants.FUTURE + "*");
        // 扫描所有topic相关的缓存键
        Set<String> topicKeys = cacheService.scan(ScheduleConstants.TOPIC + "*");
        // 删除future相关的缓存数据
        cacheService.delete(futureKeys);
        // 删除topic相关的缓存数据
        cacheService.delete(topicKeys);
    }

    /**
     * 定时刷新任务缓存
     * 该方法通过cron表达式每分钟执行一次，扫描所有future开头的缓存键，
     * 将到达执行时间的任务从future键刷新到对应的topic键中
     */
    @Scheduled(cron = "0 */1 * * * ?")
    public void refresh() {
        String token = cacheService.tryLock("FUTURE_TASK_SYNC", 1000 * 30);
        if(StringUtils.isNotBlank(token)){
            System.out.println(System.currentTimeMillis()/1000+"执行了定时任务");
            // 扫描所有future开头的缓存键
            Set<String> futureKeys = cacheService.scan(ScheduleConstants.FUTURE + "*");
            // 遍历所有future键，处理到期的任务
            for (String futureKey : futureKeys){
                // 构造对应的topic键名
                String topicKey = ScheduleConstants.TOPIC + futureKey.split(ScheduleConstants.FUTURE)[1];
                // 获取当前时间之前的所有任务
                Set<String> tasks = cacheService.zRangeByScore(futureKey, 0, System.currentTimeMillis());
                // 如果存在需要执行的任务，则刷新到topic键中
                if(!tasks.isEmpty()){
                    cacheService.refreshWithPipeline(futureKey,topicKey,tasks);
                    System.out.println("成功的将" + futureKey + "下的当前需要执行的任务数据刷新到" + topicKey + "下");
                }
            }
        }

    }

    /**
     * 从缓存中移除任务
     * 根据任务的执行时间和类型优先级，从相应的缓存队列中移除任务
     * @param task 需要移除的任务对象
     */
    private void removeTaskFromCache(Task task) {
        String key = task.getTaskType() + "_" + task.getPriority();
        // 判断任务是否已经到期
        if(task.getExecuteTime()<=System.currentTimeMillis()){
            // 从当前任务队列中移除任务
            cacheService.lRemove(ScheduleConstants.TOPIC+key,0,JSON.toJSONString(task));
        }else {
            // 从未来任务队列中移除任务
            cacheService.zRemove(ScheduleConstants.FUTURE+key,JSON.toJSONString(task));
        }
    }


    /**
     * 更新数据库中的任务状态
     * @param taskId 任务ID
     * @param status 新的任务状态
     * @return 更新后的任务对象，如果更新失败则返回null
     */
    private Task updateDb(long taskId, int status) {
        Task task=null;
        try {
            // 删除taskinfo表中的记录
            taskinfoMapper.deleteById(taskId);

            // 查询并更新taskinfo_logs表中的状态
            TaskinfoLogs taskinfoLogs = taskinfoLogsMapper.selectById(taskId);
            taskinfoLogs.setStatus(status);
            taskinfoLogsMapper.updateById(taskinfoLogs);

            // 将TaskinfoLogs对象转换为Task对象
            task = new Task();
            BeanUtils.copyProperties(taskinfoLogs,task);
            task.setExecuteTime(taskinfoLogs.getExecuteTime().getTime());
        }catch (Exception e){
            log.error("task cancel exception taskid={}",taskId);
        }
        return task;
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
