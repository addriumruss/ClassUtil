#pragma once

#include <string>
#include <time.h>
#include <sys/time.h>
#include <unistd.h>
#include <cstring>

//nlohmann/json
#include <nlohmann/json.hpp>
using json = nlohmann::json;

#ifndef DEBUG
#define DEBUG 1
#endif

#define debug_printf if(DEBUG) printf
#define MAX_FIELD_NAME_LEN 128

class JsonBase{
public:
	JsonBase(){};
	virtual ~JsonBase(){};
public:
	virtual JsonBase* parseFromJson(const json& json){return NULL;};
	virtual char* toJsonString(){return NULL;};

public:
	long sn = 0;
};

