# ClassUtil
ClassUtil是一个原型类工具，编写一次原型脚本, 可以同时生成Java原型类和C++原型类. 主要用于Java跟C++通信的场合。

下载源码后修改main函数中脚本文件路径即可使用。

脚本文件采用thrift语法, 
目前支持thrift基础数据类型以及list,set,map三种集合类型，其中集合类型最多嵌套两层; 
支持脚本之间include引用; 
支持命名空间设置;
脚本中存在的注释自动写入到Java和C++结果代码中。

输出的C++代码自动引用了jsoncpp库，可以自动从json中解析对象, 可以一键toJsonString。 
json库地址在这里: 
https://github.com/addriumruss/json

遇到问题请随时提问, 欢迎探讨~

示例:
thrift脚本(详见demo\Demo.thrift文件): 
namespace cpp demo
namespace java com.shg.edu.sch
//include "other.thrift"
/** 这个是学校对象 */
struct School{
    /** 名称, 注意字段需要附带索引和optional或required标志 */
	1: optional string name, 
	/** 序号 */
	2: optional i64 sn = 0l, 
}
/** 这个是学生对象 */
struct Student{
    /** 名称, 注意字段需要附带索引和optional或required标志 */
	1: optional string name, 
	/** 消息的序列号 */
	2: optional i32 id = 128, 
}


生成的c++代码:
demo\cpp\demo.h

生成的Java代码:
demo\java\com\shg\edu\sch\School.java
demo\java\com\shg\edu\sch\Student.java


