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
#include "demo.h"

using namespace std;
using namespace demo;

/** 返回对象指针 */
School* getClassInstanceOfSchool() {
    School *instance = new School();
    instance->name = "hello,world";
    instance->sn = 2018;

    return instance;
}

/** 返回对象指针 */
Student* getClassInstanceOfStudent() {
    Student *instance = new Student();
    instance->name = "hello,world";
    instance->id = 2018;

    return instance;
}


/** 测试入口 */
int main(int argc, char **argv) {
    int okCnt = 0, failCnt = 0;

    try {
        // 测试 School
        School *instanceSchool = getClassInstanceOfSchool();
        // json序列化 
        char* jsonStrSchool = instanceSchool->toJsonString();
        printf("School.toJsonString=%s \n", jsonStrSchool);
        // json反序列化1 
        json jsonSchool = json::parse(jsonStrSchool);
        printf("School.jsonDumpStr=%s \n", jsonSchool.dump().c_str());
        // json反序列化2 
        School *instanceSchool2 = new School();
        printf("parsing from json \n");
        instanceSchool2->parseFromJson(jsonSchool);
        printf("parsing over \n");
        char* jsonStrSchool2 = instanceSchool2->toJsonString();
        printf("School.toJsonString=%s \n", jsonStrSchool2);
        // 正确性比较 
        if(strcmp(jsonStrSchool,jsonStrSchool2) == 0) {
            okCnt++;
            printf("OK     School \n\n");
        } else {
            failCnt++;
            printf("Failed School \n\n");
        }
        // 释放资源 
        free(jsonStrSchool2);
        jsonStrSchool2 = NULL;
        delete instanceSchool2;
        instanceSchool2 = NULL;
        free(jsonStrSchool);
        jsonStrSchool = NULL;
        delete instanceSchool;
        instanceSchool = NULL;
        //release over 

    } catch(const json::parse_error& e){
        printf("JSON解析异常: %s, \n测试类名:%s \n", e.what(), "School");
        exit(-1);
    } catch(exception& e){
        printf("处理异常: %s, \n测试类名:%s \n", e.what(), "School");
        exit(-1);
    } catch(...){
        printf("处理异常: 未知的异常, \n测试类名:%s \n", "School");
        exit(-1);
    }
    try {
        // 测试 Student
        Student *instanceStudent = getClassInstanceOfStudent();
        // json序列化 
        char* jsonStrStudent = instanceStudent->toJsonString();
        printf("Student.toJsonString=%s \n", jsonStrStudent);
        // json反序列化1 
        json jsonStudent = json::parse(jsonStrStudent);
        printf("Student.jsonDumpStr=%s \n", jsonStudent.dump().c_str());
        // json反序列化2 
        Student *instanceStudent2 = new Student();
        printf("parsing from json \n");
        instanceStudent2->parseFromJson(jsonStudent);
        printf("parsing over \n");
        char* jsonStrStudent2 = instanceStudent2->toJsonString();
        printf("Student.toJsonString=%s \n", jsonStrStudent2);
        // 正确性比较 
        if(strcmp(jsonStrStudent,jsonStrStudent2) == 0) {
            okCnt++;
            printf("OK     Student \n\n");
        } else {
            failCnt++;
            printf("Failed Student \n\n");
        }
        // 释放资源 
        free(jsonStrStudent2);
        jsonStrStudent2 = NULL;
        delete instanceStudent2;
        instanceStudent2 = NULL;
        free(jsonStrStudent);
        jsonStrStudent = NULL;
        delete instanceStudent;
        instanceStudent = NULL;
        //release over 

    } catch(const json::parse_error& e){
        printf("JSON解析异常: %s, \n测试类名:%s \n", e.what(), "Student");
        exit(-1);
    } catch(exception& e){
        printf("处理异常: %s, \n测试类名:%s \n", e.what(), "Student");
        exit(-1);
    } catch(...){
        printf("处理异常: 未知的异常, \n测试类名:%s \n", "Student");
        exit(-1);
    }

    printf("OK count: %d, Fail count: %d \n", okCnt, failCnt);
    return 0;
}

