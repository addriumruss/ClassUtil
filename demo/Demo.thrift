/**
通用基础数据类定义
2018-09-20, russ
*/

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
