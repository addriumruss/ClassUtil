#pragma once

#include <stdio.h>
#include <stdlib.h>
#include <string>
#include <algorithm>
#include <array>
#include <ciso646>
#include <forward_list>
#include <iterator>
#include <map>
#include <vector>
#include <set>
#include <tuple>
#include <type_traits>
#include <unordered_map>
#include <utility>
#include <valarray>

//nlohmann/json
#include <nlohmann/json.hpp>
using json = nlohmann::json;

#include "BaseTypes.h"

using namespace std;

namespace demo { 
/**
通用基础数据类定义
2018-09-20, russ
*/


//include "other.thrift"

/** 这个是学校对象 */
class School: public JsonBase {
public: 
    /** 名称, 注意字段需要附带索引和optional或required标志 */
    string name;
    /** 序号 */
    int64_t sn = 0l;
public: 
    /** 注意: 返回结果需要delete释放! */
    virtual School* parseFromJson(const json& jsonObj) {
        const string jsonObjDumpString = jsonObj.dump(); /* 必须先传输到string结构中才能有效 */
        const char *dumpstr = jsonObjDumpString.c_str();
        debug_printf("[info] School json.dump= %s \n", dumpstr);
        debug_printf("[info] School 解析属性: %s \n", "name");
        if(strstr(dumpstr, "\"name\"")) {
            name = jsonObj["name"];
        }
        debug_printf("[info] School 解析属性: %s \n", "sn");
        if(strstr(dumpstr, "\"sn\"")) {
            sn = jsonObj["sn"];
        }
        return this;
    }
    /** 注意: 返回结果需要free释放! */
    virtual char* toJsonString() {
        debug_printf("School 构建json对象 \n");
        json jsonObj;
        debug_printf("School 序列化字段: %s\n ","name");
        if(!name.empty()) {
            jsonObj["name"] = name;
        }
        debug_printf("School 序列化字段: %s\n ","sn");
        jsonObj["sn"] = sn;

        debug_printf("School 创建并输出字符串 \n");
        string jsonStr = jsonObj.dump();
        int jsonLen = jsonStr.length();
        char *res = (char*)malloc(jsonLen+1);
        if(!res) {
            printf("School 内存不足, toJsonString失败!\n");
            return NULL;
        }
        memcpy(res, jsonStr.c_str(), jsonLen);
        res[jsonLen] = 0;
        return res;
    }
public:
    School() {
        sn = 0;
    }
    virtual ~School() {
        // 开始释放对象 
        //TODO, 在这里添加额外的释放处理
    }
};

/** 这个是学生对象 */
class Student: public JsonBase {
public: 
    /** 名称, 注意字段需要附带索引和optional或required标志 */
    string name;
    /** 消息的序列号 */
    int32_t id = 128;
public: 
    /** 注意: 返回结果需要delete释放! */
    virtual Student* parseFromJson(const json& jsonObj) {
        const string jsonObjDumpString = jsonObj.dump(); /* 必须先传输到string结构中才能有效 */
        const char *dumpstr = jsonObjDumpString.c_str();
        debug_printf("[info] Student json.dump= %s \n", dumpstr);
        debug_printf("[info] Student 解析属性: %s \n", "name");
        if(strstr(dumpstr, "\"name\"")) {
            name = jsonObj["name"];
        }
        debug_printf("[info] Student 解析属性: %s \n", "id");
        if(strstr(dumpstr, "\"id\"")) {
            id = jsonObj["id"];
        }
        return this;
    }
    /** 注意: 返回结果需要free释放! */
    virtual char* toJsonString() {
        debug_printf("Student 构建json对象 \n");
        json jsonObj;
        debug_printf("Student 序列化字段: %s\n ","name");
        if(!name.empty()) {
            jsonObj["name"] = name;
        }
        debug_printf("Student 序列化字段: %s\n ","id");
        jsonObj["id"] = id;

        debug_printf("Student 创建并输出字符串 \n");
        string jsonStr = jsonObj.dump();
        int jsonLen = jsonStr.length();
        char *res = (char*)malloc(jsonLen+1);
        if(!res) {
            printf("Student 内存不足, toJsonString失败!\n");
            return NULL;
        }
        memcpy(res, jsonStr.c_str(), jsonLen);
        res[jsonLen] = 0;
        return res;
    }
public:
    Student() {
        id = 0;
    }
    virtual ~Student() {
        // 开始释放对象 
        //TODO, 在这里添加额外的释放处理
    }
};

} 
