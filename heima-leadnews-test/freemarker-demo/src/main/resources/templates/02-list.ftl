<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <title>Hello World!</title>
</head>
<body>

<#-- list 数据的展示 -->
<b>展示list中的stu数据:</b>
<br>
<br>
<table>
    <tr>
        <td>数据量:${stus?size}</td>
        <td>序号</td>
        <td>姓名</td>
        <td>年龄</td>
        <td>钱包</td>
    </tr>
    <#list stus as stu>
        <#if stu.name='李四'>
            <tr style="color: red">
                <td>${stu_index+1}</td>
                <td>${stu.name}</td>
                <td>${stu.age}</td>
                <td>${stu.money}</td>
            </tr>
            <#else >
                <tr>
                    <td>${stu_index+1}</td>
                    <td>${stu.name}</td>
                    <td>${stu.age}</td>
                    <td>${stu.money}</td>
                </tr>
        </#if>
    </#list>
</table>
<hr>

<#-- Map 数据的展示 -->
<b>map数据的展示：</b>
<br/><br/>
<a href="###">方式一：通过map['keyname'].property</a><br/>
输出stu1的学生信息：<br/>
姓名：${stuMap['stu1'].name}<br/>
年龄：${stuMap['stu1'].age}<br/>
<br/>
<a href="###">方式二：通过map.keyname.property</a><br/>
输出stu2的学生信息：<br/>
姓名：${stuMap.stu2.name}<br/>
年龄：${stuMap.stu2.age}<br/>

<br/>
<a href="###">遍历map中两个学生信息：</a><br/>
<table>
    <tr>
        <td>序号</td>
        <td>姓名</td>
        <td>年龄</td>
        <td>钱包</td>
    </tr>
    <#list stuMap?keys as key>
        <tr>
            <td>${key_index+1}</td>
            <td>${stuMap[key].name}</td>
            <td>${stuMap[key].age}</td>
            <td>${stuMap[key].money}</td>
        </tr>
    </#list>
</table>
<hr>
<b>算数运算</b>
<br/>
100+5=${100+5}<br/>
100-5=${100-5}<br/>
100*5=${100*5}<br/>
100/5=${100/5}<br/>
<hr>
<b>??存在检验</b>
<br/>
<#if exit??>
    存在${ exit}<br/>
    <#else >
    不存在<br/>
</#if>
<hr>
<b>!存在检验</b>
<br/>
${exit2!'我不存在'}
<hr>
<b>各种内置函数</b>
<br/>
${today?date}<br/>
${today?time}<br/>
${today?datetime}<br/>
${today?string("yyyy年MM月dd日 HH:mm:ss")}<br/>
<hr>
<b>json字符串转对象</b><br/>
<#assign text="{'name':'张三', 'age':18,'gender':'男'}"/>
<#assign data=text?eval/>
学生姓名：${data.name}  学生年龄：${data.age}  学生性别：${data.gender}
</body>
</html>