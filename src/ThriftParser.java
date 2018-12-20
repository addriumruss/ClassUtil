import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ThriftParser {
	private static final Log log = LogFactory.getLog(ThriftParser.class);
	public static final String NL = "\r\n";
	//public static final String NAMESPACE = "schedule";
	public static final String NAMESPACE = "demo";
	public static final String CHAR_REGEX = "^[a-z0-9A-Z]+$";
	public static final String ConstantSuffix = "Constants";
	public String javaFieldModifier = "public";
	public static final DateFormat SDF5 = new SimpleDateFormat("yyyy-MM-dd");
	public static final String PRODUCT = "ClassUtil";
	public static final String VERSION = "1.0.0.0";
	
	public static final List<String> cppBaseTypes = new ArrayList<>();
	private static final Map<String, Map<String, Integer>> enumTypeMap = new HashMap<>();
	public static final List<String> classTypeList = new ArrayList<>();
	
	private static final Map<Integer, String> spaceMap = new HashMap<>();
	private static int indent = 0;
	
	private static final List<String> javaDigitTypes = new ArrayList<>();
	
	//测试信息
	public static final StringBuilder testRes = new StringBuilder(8192);
	private static int testIndent = 0;
	static List<String> targetFileNameList = new ArrayList<>();
	
	
	static {
		cppBaseTypes.add("string");
		cppBaseTypes.add("double");
		cppBaseTypes.add("int");
		cppBaseTypes.add("short");
		cppBaseTypes.add("bool");
		cppBaseTypes.add("int32_t");
		cppBaseTypes.add("int64_t");
		cppBaseTypes.add("char");
		cppBaseTypes.add("unsigned char*");
		cppBaseTypes.add("char*");
		cppBaseTypes.add("const string");
		
		String spaceUnit = "    ";
		for(int i=0;i<20;i++) {
			String value = "";
			for(int j=0;j<i;j++) {
				value += spaceUnit;
			}
			spaceMap.put(i, value);
		}	
	
		
		Map<String, Integer> stateMap = new HashMap<>();
		stateMap.put("FAIL", 0);
		stateMap.put("SUCCESS", 1);
		stateMap.put("EXCEPTION", 2);
		stateMap.put("PUSH", 3);
		stateMap.put("REDIRECT", 4);
		stateMap.put("BLOCK", 5);
		stateMap.put("CODE", 6);
		stateMap.put("SPECIAL", 7);
		stateMap.put("REPEAT", 8);
		stateMap.put("FORBIDDEN", 9);
		enumTypeMap.put("State", stateMap);
		
		javaDigitTypes.add("Integer");
		javaDigitTypes.add("int");
		javaDigitTypes.add("Short");
		javaDigitTypes.add("short");
		javaDigitTypes.add("Float");
		javaDigitTypes.add("float");
		javaDigitTypes.add("Double");
		javaDigitTypes.add("double");
	}
	
	public static final void log(String text) {
		log.info(text);
		//System.out.println(text);
	}

	//生成c++代码
	public boolean genCpp(String thriftFilePath, String targetFilePath) throws Exception {
		File srcfile = new File(thriftFilePath);
		if(!srcfile.exists()) {
			log("源文件不存在: " + thriftFilePath);
			return false;
		}
		String srcFileName = FilenameUtils.getName(thriftFilePath);
		List<String> lines = FileUtils.readLines(srcfile, StandardCharsets.UTF_8);
		int lineCount = lines.size();
		List<String> headers = new ArrayList<>();
		targetFileNameList.add(FilenameUtils.getName(targetFilePath));
		
		//<变量名称, 类型>
		Map<String, String> fieldMap = new HashMap<>();
		
		String className = null;
		
		StringBuilder res = new StringBuilder(4096);
		addVersion(res);
		res.append("#pragma once").append(NL).append(NL);
		res.append("#include <stdio.h>").append(NL);
		res.append("#include <stdlib.h>").append(NL);
		res.append("#include <string>").append(NL);
		res.append("#include <algorithm>").append(NL);
		res.append("#include <array>").append(NL);
		res.append("#include <ciso646>").append(NL);
		res.append("#include <forward_list>").append(NL);
		res.append("#include <iterator>").append(NL);
		res.append("#include <map>").append(NL);
		res.append("#include <vector>").append(NL);
		res.append("#include <set>").append(NL);
		res.append("#include <tuple>").append(NL);
		res.append("#include <type_traits>").append(NL);
		res.append("#include <unordered_map>").append(NL);
		res.append("#include <utility>").append(NL);
		res.append("#include <valarray>").append(NL);
		res.append(NL).append("#include \"BaseTypes.h\"").append(NL);
		res.append(NL).append("//nlohmann/json").append(NL);
		res.append("#include <nlohmann/json.hpp>").append(NL);
		res.append("using json = nlohmann::json;").append(NL);
		res.append(NL).append("using namespace std;").append(NL);
		int headLen = res.length();
		res.append(NL).append("namespace ").append(NAMESPACE).append(" { ").append(NL);
		boolean isClass = false;
		boolean isEnum = false;
		indent = 0;
		for(int i=0;i<lineCount;i++) {
			String lineOri = lines.get(i);
			String line = lineOri.trim();
			if(line.startsWith("namespace")) {
				continue;
			}
			if(line.contains("/*") 
					|| line.startsWith("*")
					|| line.contains("*/") 
					 || line.startsWith("//") 
					 || line.startsWith("#")
					 || line.length() == 0) {
				res.append(spaceMap.get(indent)).append(line).append(NL);
				continue;
			}
			if(line.startsWith("include")) {
				lineOri = lineOri.replace("include", "#include")
						.replace(".thrift", ".h");
				res.insert(headLen, NL + lineOri + NL + NL);
				String headerName = line.substring(line.indexOf('\"') + 1, line.indexOf('.'));
				headers.add(headerName);
				log("found header " + headerName);
				continue;
			}
			lineOri = cppLinefilter(lineOri);
			line = cppLinefilter(line).trim();
			if(line.startsWith("const")) {
				res.append(spaceMap.get(indent)).append(line).append(NL);
				//解析map
				if(line.contains("map<")) {
					i++;
					indent++;
					while(!(lineOri = lines.get(i)).contains("};")) {
						String[] arr = lineOri.trim().split(":");
						String left = arr[0].trim();
						String right = arr[1].trim();
						if(right.endsWith(",")) {
							line = "{"+left+", "+right.substring(0, right.length() -1) + "},";
						}else {
							line = "{"+left+", "+right + "}";
						}
						res.append(spaceMap.get(indent)).append(line).append(NL);
						i++;
					}
					decreaseIndent();
					res.append(spaceMap.get(indent)).append(lineOri.trim()).append(NL);
				}
				continue;
			}
			//收集enum类型
			if(line.startsWith("enum")) {
				isEnum = true;
				String enumName = line.split("enum")[1].trim();
				if(enumName.endsWith("{")) {
					enumName = enumName.substring(0, enumName.length() - 1).trim();
				}
				Map<String, Integer> newEnumMap = new HashMap<>();
				enumTypeMap.put(enumName, newEnumMap);
				log("found enum " + enumName);
				//一次性读取
				res.append(spaceMap.get(indent)).append(line).append(NL);
				int lastEnumValue = 0;
				indent++;
				while(!"}".equals(line)) {
					i++;
					lineOri = lines.get(i);
					line = lineOri.trim();
					if("}".equals(line))
						break;
					if(line.startsWith("/*")) {
						res.append(spaceMap.get(indent)).append(line).append(NL);
						while(!line.endsWith("*/")) {
							i++;
							lineOri = lines.get(i);
							line = lineOri.trim();
							res.append(spaceMap.get(indent)).append(line).append(NL);
						}
					}else if(line.startsWith("//") || line.length() == 0){
						res.append(spaceMap.get(indent)).append(line).append(NL);
					}else {
						if(line.endsWith(",") || line.endsWith(";"))
							line = line.substring(0, line.length() - 1);
						String[] arr = line.split("=");
						if(arr.length == 1) {
							String enumDef = arr[0].trim();
							newEnumMap.put(enumDef, lastEnumValue);
							res.append(spaceMap.get(indent)).append(line.replace(enumDef, enumDef + " = " + lastEnumValue)).append(",").append(NL);
							lastEnumValue++;
						}else if(arr.length == 2){
							String enumDef = arr[0].trim();
							int v = Integer.parseInt(arr[1].trim());
							if(newEnumMap.containsValue(v))
								throw new Exception("枚举值与前面定义重复! line="+lineOri+", className="+className);
							lastEnumValue = v;
							newEnumMap.put(enumDef, lastEnumValue);
							lastEnumValue++;
							
							res.append(spaceMap.get(indent)).append(line).append(",").append(NL);
						}else {
							throw new Exception("语法错误! line="+lineOri);
						}
					}
				}
				decreaseIndent();
				res.append(spaceMap.get(indent)).append(line).append(";").append(NL);
				isEnum = false;
				continue;
			}
			//解析类名
			if(line.startsWith("struct")) {
				isClass = true;
				fieldMap.clear();
				lineOri = lineOri.replace("struct", "class")
						.replace("{", ": public JsonBase {");
				className = line.split("struct")[1].trim();
				if(className.endsWith("{")) {
					className = className.substring(0, className.length() - 1).trim();
				}
				res.append(spaceMap.get(indent)).append(lineOri.trim()).append(NL);
				decreaseIndent();
				res.append(spaceMap.get(indent)).append("public: ").append(NL);
				indent++;
				log("found class " + className);
				continue;
			}
			if("DeleteGeofenceResultPara".equals(className)) {
				log("stop");
			}
			//解析成员变量
			if(line.contains("optional") || line.contains("required")) {
				line = lineOri.split("optional")[1].trim();
				if(line.endsWith(",") || line.endsWith(";")) {
					line = line.substring(0, line.length() -1).trim();
				}
				for(String headerName : headers) {
					if(line.contains(headerName + ".")) {
						line = line.replace(headerName+".", "");
					}
				}
				for(String enumName : enumTypeMap.keySet()) {
					if(line.contains(enumName + ".")) {
						line = line.replace(enumName+".", "");
					}
				}
				
				//保存变量名称和类型
				String templine = line;
				if(templine.indexOf('=') > 0) {
					templine = templine.split("=")[0].trim();
				}
				String[] arr = templine.split(" ");
				if(arr.length < 2) {
					throw new Exception("未知的数据类型, line="+lineOri);
				}
				String fieldName = arr[arr.length - 1].trim();
				String typeName = templine.substring(0, templine.lastIndexOf(' ')).trim();
				fieldMap.put(fieldName, typeName);
				log.info("已保存变量: " + typeName+" => " + fieldName);
				
				if(isPointer(typeName)) {
					String tempTypeName = convertToPtr(typeName, className);
					res.append(spaceMap.get(indent)).append(tempTypeName).append(" *").append(fieldName).append(" = NULL;").append(NL);
				}else {
					line += ";";
					res.append(spaceMap.get(indent)).append(line).append(NL);
				}
				continue;
			}
			
			if("}".equals(line)) {
				decreaseIndent();
				if(isClass) {
					//为类添加json解析
					decreaseIndent();
					res.append(spaceMap.get(indent)).append("public: ").append(NL);
					indent++;
					res.append(spaceMap.get(indent)).append("/** 注意: 返回结果需要delete释放! */").append(NL);
					res.append(spaceMap.get(indent)).append("virtual ").append(className)
						.append("* parseFromJson(const json& jsonObj) {").append(NL);
					indent++;
					res.append(spaceMap.get(indent)).append("const string jsonObjDumpString = jsonObj.dump(); /* 必须先传输到string结构中才能有效 */").append(NL);
					res.append(spaceMap.get(indent)).append("const char *dumpstr = jsonObjDumpString.c_str();").append(NL);
					res.append(spaceMap.get(indent)).append("debug_printf(\"[info] ").append(className).append(" json.dump= %s \\n\", dumpstr);").append(NL); //TESTING
					if(fieldMap.values().contains("unsigned char*")) {
						res.append(spaceMap.get(indent)).append("string tempbinstr;").append(NL);
					}
					for(Entry<String,String> entry : fieldMap.entrySet()) {
						String fieldName = entry.getKey();
						String fieldType = entry.getValue();
						res.append(spaceMap.get(indent)).append("debug_printf(\"[info] ").append(className).append(" 解析属性: %s \\n\", \"").append(fieldName).append("\");").append(NL); //TESTING
						res.append(spaceMap.get(indent)).append("if(strstr(dumpstr, \"\\\"").append(fieldName).append("\\\"\")) {").append(NL); //if begin
						indent++;
						if(cppBaseTypes.contains(fieldType)) {
							fillBaseType(res, fieldType, fieldName, "jsonObj");
						}else if(fieldType.startsWith("map")) {
							String typeKey = fieldType.substring(fieldType.indexOf('<')+1, fieldType.indexOf(','));
							String typeValue = fieldType.substring(fieldType.indexOf(',')+1, fieldType.length() - 1).trim();
							res.append(spaceMap.get(indent)).append(fieldName).append(" = new map<").append(convertToPtr2(typeKey, className)).append(",").append(convertToPtr2(typeValue, className)).append(">();").append(NL);
							res.append(spaceMap.get(indent)).append("for (auto& item : jsonObj[\"").append(fieldName).append("\"].items()){").append(NL);
							indent++;
							
							//key一定是原始类型
							res.append(spaceMap.get(indent)).append(convertToPtr2(typeKey, className)).append(" key = item.key();").append(NL);
							//值可能是对象
							if(cppBaseTypes.contains(typeValue)) {
								//res.append(spaceMap.get(indent)).append(typeValue).append(" value = item.value();").append(NL);
								String value = appValueOfBaseType(res, typeValue, "value", "item.value()");
								//将值加入第一层map
								res.append(spaceMap.get(indent)).append(fieldName).append("->insert(pair<").append(typeKey).append(',').append(typeValue).append(">(")
								.append("key, ").append(value).append("));").append(NL);
							}else if(typeValue.startsWith("map")){
								String typeKey2 = typeValue.substring(typeValue.indexOf('<')+1, typeValue.indexOf(','));
								String typeValue2 = typeValue.substring(typeValue.indexOf(',')+1, typeValue.length() - 1).trim();
								if(!cppBaseTypes.contains(typeValue2)) {
									throw new Exception("map最多嵌套两层! line="+lineOri);
								}
								res.append(spaceMap.get(indent)).append("map<").append(typeKey2).append(",").append(typeValue2).append("> *tempmap = new map<")
								.append(typeKey2).append(",").append(typeValue2).append(">();").append(NL);
								res.append(spaceMap.get(indent)).append("for (auto& item2 : item.value()").append(".items()){").append(NL);
								indent++;
								
								//key一定是原始类型
								res.append(spaceMap.get(indent)).append(typeKey2).append(" key2 = item2.key();").append(NL);
								//res.append(spaceMap.get(indent)).append(typeValue2).append(" value2 = item2.value();").append(NL);
								String value2 = appValueOfBaseType(res, typeValue2, "value2", "item2.value()");
								//将值加入第二层map
								res.append(spaceMap.get(indent)).append("tempmap->insert(pair<").append(typeKey2).append(',').append(typeValue2).append(">(")
								.append("key2, ").append(value2).append("));").append(NL);
								decreaseIndent();
								res.append(spaceMap.get(indent)).append("}").append(NL);
								
								//将值加入第一层map
								res.append(spaceMap.get(indent)).append(fieldName).append("->insert(pair<").append(convertToPtr2(typeKey, className)).append(',').append(convertToPtr2(typeValue, className)).append(">(")
								.append("key, tempmap));").append(NL);
							}else if(typeValue.startsWith("vector")){
								String listValueType = typeValue.substring(typeValue.indexOf('<') + 1, typeValue.lastIndexOf('>'));
								if(!cppBaseTypes.contains(listValueType)) {
									throw new Exception("list最多嵌套两层! line="+lineOri+", className="+className);
								}
								res.append(spaceMap.get(indent)).append("vector<").append(listValueType).append("> *templist = new vector<")
								.append(listValueType).append(">();").append(NL);
								res.append(spaceMap.get(indent)).append("for (auto& item3 : item.value()").append(".items()){").append(NL);
								indent++;
								String value3 = appValueOfBaseType(res, listValueType, "value3", "item3.value()");
								res.append(spaceMap.get(indent)).append("templist->push_back(").append(value3).append(");").append(NL);
								decreaseIndent();
								res.append(spaceMap.get(indent)).append("}").append(NL);
								
								//将值加入第一层map
								res.append(spaceMap.get(indent)).append(fieldName).append("->insert(pair<").append(convertToPtr2(typeKey, className)).append(',').append(convertToPtr2(typeValue, className)).append(">(")
								.append("key, templist));").append(NL);
							}else if(typeValue.startsWith("set")){
								String setValueType = typeValue.substring(typeValue.indexOf('<') + 1, typeValue.lastIndexOf('>'));
								if(!cppBaseTypes.contains(setValueType)) {
									throw new Exception("set最多嵌套两层! line="+lineOri);
								}
								res.append(spaceMap.get(indent)).append("set<").append(setValueType).append("> *tempset = new set<")
								.append(setValueType).append(">();").append(NL);
								res.append(spaceMap.get(indent)).append("for (auto& item4 : item.value()").append(".items()){").append(NL);
								indent++;
								String value4 = appValueOfBaseType(res, setValueType, "value4", "item4.value()");
								res.append(spaceMap.get(indent)).append("tempset->insert(").append(value4).append(");").append(NL);
								decreaseIndent();
								res.append(spaceMap.get(indent)).append("}").append(NL);
								
								//将值加入第一层map
								res.append(spaceMap.get(indent)).append(fieldName).append("->insert(pair<").append(convertToPtr2(typeKey, className)).append(',').append(convertToPtr2(typeValue, className)).append(">(")
								.append("key, tempset));").append(NL);
							}else if(enumTypeMap.containsKey(typeValue)){
								//枚举类型
								res.append(spaceMap.get(indent)).append("{").append(NL);
								indent++;
								res.append(spaceMap.get(indent)).append("json value = item.value();").append(NL);
								res.append(spaceMap.get(indent)).append(typeValue).append(" enumValue;").append(NL);
								//fillEnum(res, typeValue, fieldName, "enumKey");
								Map<String, Integer> enumMap = enumTypeMap.get(typeValue);
								boolean nonfirst = false;
								for(Entry<String, Integer> entry1 : enumMap.entrySet()) {
									if(nonfirst) {
										res.append(" else ");
									}else {
										res.append(spaceMap.get(indent));
										nonfirst = true;
									}
									res.append("if(strcmp(value.get<string>().c_str(), \"").append(entry1.getKey()).append("\") == 0) {").append(NL);
									indent++;
									res.append(spaceMap.get(indent)).append("enumValue = ").append(entry1.getKey()).append(";").append(NL);
									decreaseIndent();
									res.append(spaceMap.get(indent)).append("}");
								}
								res.append(" else {").append(NL);
								indent++;
								res.append(spaceMap.get(indent)).append("printf(\"未知的枚举值: ").append(typeValue).append("\\n\");").append(NL);
								decreaseIndent();
								res.append(spaceMap.get(indent)).append("}").append(NL);
								
								//将值加入第一层map
								res.append(spaceMap.get(indent)).append(fieldName).append("->insert(pair<").append(convertToPtr2(typeKey, className)).append(',').append(convertToPtr2(typeValue, className)).append(">(")
								.append("key, enumValue));").append(NL);
								decreaseIndent();
								res.append(spaceMap.get(indent)).append("}").append(NL);
							}else if(classTypeList.contains(typeValue)){
								//自定义的类
								res.append(spaceMap.get(indent)).append(typeValue).append(" *vinst = new ").append(typeValue).append("();").append(NL);
								res.append(spaceMap.get(indent)).append("vinst->parseFromJson(item.value());").append(NL);
								res.append(spaceMap.get(indent)).append(fieldName).append("->insert(pair<").append(convertToPtr2(typeKey, className)).append(',').append(convertToPtr2(typeValue, className)).append(">(")
								.append("key, vinst));").append(NL);
							}else {
								throw new Exception("未支持的数据类型map(2): " + typeValue + ", srcFileName=" + srcFileName+", className="+className);
							}
							decreaseIndent();
							res.append(spaceMap.get(indent)).append("}").append(NL).append(NL);
						}else if(fieldType.startsWith("vector")) {
							String listValueType = fieldType.substring(fieldType.indexOf('<') + 1, fieldType.lastIndexOf('>'));
							res.append(spaceMap.get(indent)).append(fieldName).append(" = new vector<").append(convertToPtr2(listValueType, className)).append(">();").append(NL);
							res.append(spaceMap.get(indent)).append("for (auto& listItem : jsonObj[\"").append(fieldName).append("\"].items()){").append(NL);
							indent++;
							//值可能是对象
							if(cppBaseTypes.contains(listValueType)) {
								String value = appValueOfBaseType(res, listValueType, "value", "listItem.value()");
								//将值加入第一层list
								res.append(spaceMap.get(indent)).append(fieldName).append("->push_back(").append(value).append(");").append(NL);
							}else if(listValueType.startsWith("map")){
								String typeKey2 = listValueType.substring(listValueType.indexOf('<')+1, listValueType.indexOf(','));
								String typeValue2 = listValueType.substring(listValueType.indexOf(',')+1, listValueType.length() - 1).trim();
								if(!cppBaseTypes.contains(typeValue2)) {
									throw new Exception("map最多嵌套两层! line="+lineOri);
								}
								res.append(spaceMap.get(indent)).append("map<").append(typeKey2).append(",").append(typeValue2).append("> *tempmap = new map<")
								.append(typeKey2).append(",").append(typeValue2).append(">();").append(NL);
								res.append(spaceMap.get(indent)).append("for (auto& item2 : listItem.value()").append(".items()){").append(NL);
								indent++;
								
								//key一定是原始类型
								res.append(spaceMap.get(indent)).append("json key2 = item2.key();").append(NL);
								//res.append(spaceMap.get(indent)).append("json value2 = item2.value();").append(NL);
								String value2 = appValueOfBaseType(res, typeValue2, "value2", "item2.value()");
								//将值加入第二层map
								res.append(spaceMap.get(indent)).append("tempmap->insert(pair<").append(typeKey2).append(',').append(typeValue2).append(">(")
								.append("key2, ").append(value2).append("));").append(NL);
								decreaseIndent();
								res.append(spaceMap.get(indent)).append("}").append(NL);
								
								//将值加入第一层list
								res.append(spaceMap.get(indent)).append(fieldName).append("->push_back(tempmap);").append(NL);
							}else if(listValueType.startsWith("vector")){
								String listValueType2 = listValueType.substring(listValueType.indexOf('<') + 1, listValueType.lastIndexOf('>'));
								if(!cppBaseTypes.contains(listValueType2)) {
									throw new Exception("list最多嵌套两层! line="+lineOri);
								}
								res.append(spaceMap.get(indent)).append("vector<").append(listValueType2).append("> *templist = new vector<")
								.append(listValueType2).append(">();").append(NL);
								res.append(spaceMap.get(indent)).append("for (auto& item3 : listItem.value()").append(".items()){").append(NL);
								indent++;
								String value3 = appValueOfBaseType(res, listValueType2, "value3", "item3.value()");
								res.append(spaceMap.get(indent)).append("templist->push_back(").append(value3).append(");").append(NL);
								decreaseIndent();
								res.append(spaceMap.get(indent)).append("}").append(NL);
								
								//将值加入第一层list
								res.append(spaceMap.get(indent)).append(fieldName).append("->push_back(templist);").append(NL);
							}else if(listValueType.startsWith("set")){
								String setValueType = listValueType.substring(listValueType.indexOf('<') + 1, listValueType.lastIndexOf('>'));
								if(!cppBaseTypes.contains(setValueType)) {
									throw new Exception("set最多嵌套两层! line="+lineOri);
								}
								res.append(spaceMap.get(indent)).append("set<").append(setValueType).append("> *tempset = new set<")
								.append(setValueType).append(">();").append(NL);
								res.append(spaceMap.get(indent)).append("for (auto& item4 : listItem.value()").append(".items()){").append(NL);
								indent++;
								String value4 = appValueOfBaseType(res, setValueType, "value4", "item4.value()");
								res.append(spaceMap.get(indent)).append("tempset->insert(").append(value4).append(");").append(NL);
								decreaseIndent();
								res.append(spaceMap.get(indent)).append("}").append(NL);
								
								//将值加入第一层list
								res.append(spaceMap.get(indent)).append(fieldName).append("->push_back(tempset);").append(NL);
							}else if(enumTypeMap.containsKey(listValueType)){
								//枚举类型
								res.append(spaceMap.get(indent)).append("{").append(NL);
								indent++;
								res.append(spaceMap.get(indent)).append(listValueType).append(" enumValue;").append(NL);
								//fillEnum(res, typeValue, fieldName, "enumKey");
								Map<String, Integer> enumMap = enumTypeMap.get(listValueType);
								boolean nonfirst = false;
								for(Entry<String, Integer> entry1 : enumMap.entrySet()) {
									if(nonfirst) {
										res.append(" else ");
									}else {
										res.append(spaceMap.get(indent));
										nonfirst = true;
									}
									res.append("if(strcmp(listItem.value().get<string>().c_str(), \"").append(entry1.getKey()).append("\") == 0) {").append(NL);
									indent++;
									res.append(spaceMap.get(indent)).append("enumValue = ").append(entry1.getKey()).append(";").append(NL);
									decreaseIndent();
									res.append(spaceMap.get(indent)).append("}");
								}
								res.append(" else {").append(NL);
								indent++;
								res.append(spaceMap.get(indent)).append("printf(\"未知的枚举值: ").append(listValueType).append("\\n\");").append(NL);
								decreaseIndent();
								res.append(spaceMap.get(indent)).append("}").append(NL);
								
								//将值加入第一层list
								res.append(spaceMap.get(indent)).append(fieldName).append("->push_back(enumValue);").append(NL);
								decreaseIndent();
								res.append(spaceMap.get(indent)).append("}").append(NL);
							}else if(classTypeList.contains(listValueType)){
								//自定义的类
								//fillClass(res, fieldType, fieldName, "mapItemValue");
								res.append(spaceMap.get(indent)).append(listValueType).append(" *vinst = new ").append(listValueType).append("();").append(NL);
								res.append(spaceMap.get(indent)).append("vinst->parseFromJson(listItem.value());").append(NL);
								//将值加入第一层list
								res.append(spaceMap.get(indent)).append(fieldName).append("->push_back(vinst);").append(NL);
							}else {
								throw new Exception("未支持的数据类型list(2): " + listValueType + ", srcFileName=" + srcFileName+", className="+className);
							}
							decreaseIndent();
							res.append(spaceMap.get(indent)).append("}").append(NL);
						}else if(fieldType.startsWith("set")) {
							String setValueType0 = fieldType.substring(fieldType.indexOf('<') + 1, fieldType.lastIndexOf('>'));
							res.append(spaceMap.get(indent)).append(fieldName).append(" = new set<").append(convertToPtr2(setValueType0, className)).append(">();").append(NL);
							res.append(spaceMap.get(indent)).append("for (auto& setItem : jsonObj[\"").append(fieldName).append("\"].items()){").append(NL);
							indent++;
							//值可能是对象
							if(cppBaseTypes.contains(setValueType0)) {
								String value = appValueOfBaseType(res, setValueType0, "value", "setItem.value()");
								//将值加入第一层set
								res.append(spaceMap.get(indent)).append(fieldName).append("->insert(").append(value).append(");").append(NL);
							}else if(setValueType0.startsWith("map")){
								String typeKey2 = setValueType0.substring(setValueType0.indexOf('<')+1, setValueType0.indexOf(','));
								String typeValue2 = setValueType0.substring(setValueType0.indexOf(',')+1, setValueType0.length() - 1).trim();
								typeValue2 = typeValue2.substring(0, typeValue2.length() - 1); //remove last '>'
								if(!cppBaseTypes.contains(typeValue2)) {
									throw new Exception("map最多嵌套两层! line="+lineOri);
								}
								res.append(spaceMap.get(indent)).append("map<").append(typeKey2).append(",").append(typeValue2).append("> *tempmap = new map<")
								.append(typeKey2).append(",").append(typeValue2).append(">();").append(NL);
								res.append(spaceMap.get(indent)).append("for (auto& item2 : setItem.value()").append(".items()){").append(NL);
								indent++;
								
								//key一定是原始类型
								res.append(spaceMap.get(indent)).append("json key2 = item2.key();").append(NL);
								//res.append(spaceMap.get(indent)).append("json value2 = item2.value();").append(NL);
								String value2 = appValueOfBaseType(res, typeValue2, "value2", "item2.value()");
								//将值加入第二层map
								res.append(spaceMap.get(indent)).append("tempmap->insert(pair<").append(typeKey2).append(',').append(typeValue2).append(">(")
								.append("key2, ").append(value2).append("));").append(NL);
								
								decreaseIndent();
								res.append(spaceMap.get(indent)).append("}").append(NL);
								
								//将值加入第一层set
								res.append(spaceMap.get(indent)).append(fieldName).append("->insert(tempmap);").append(NL);
							}else if(setValueType0.startsWith("vector")){
								String listValueType2 = setValueType0.substring(setValueType0.indexOf('<') + 1, setValueType0.lastIndexOf('>'));
								if(!cppBaseTypes.contains(listValueType2)) {
									throw new Exception("list最多嵌套两层! line="+lineOri);
								}
								res.append(spaceMap.get(indent)).append("vector<").append(listValueType2).append("> *templist = new vector<")
								.append(listValueType2).append(">();").append(NL);
								res.append(spaceMap.get(indent)).append("for (auto& item3 : setItem.value()").append(".items()){").append(NL);
								indent++;
								//将值加入第二层list
								String value3 = appValueOfBaseType(res, listValueType2, "value3", "item3.value()");
								res.append(spaceMap.get(indent)).append("templist->push_back(").append(value3).append(");").append(NL);
								decreaseIndent();
								res.append(spaceMap.get(indent)).append("}").append(NL);
								
								//将值加入第一层set
								res.append(spaceMap.get(indent)).append(fieldName).append("->insert(templist);").append(NL);
							}else if(setValueType0.startsWith("set")){
								String setValueType = setValueType0.substring(setValueType0.indexOf('<') + 1, setValueType0.lastIndexOf('>'));
								if(!cppBaseTypes.contains(setValueType)) {
									throw new Exception("set最多嵌套两层! line="+lineOri);
								}
								res.append(spaceMap.get(indent)).append("set<").append(setValueType).append("> *tempset = new set<")
								.append(setValueType).append(">();").append(NL);
								res.append(spaceMap.get(indent)).append("for (auto& item4 : setItem.value()").append(".items()){").append(NL);
								indent++;
								//将值加入第二层set
								String value4 = appValueOfBaseType(res, setValueType, "value4", "item4.value()");
								res.append(spaceMap.get(indent)).append("tempset->insert(").append(value4).append(");").append(NL);
								decreaseIndent();
								res.append(spaceMap.get(indent)).append("}").append(NL);
								
								//将值加入第一层set
								res.append(spaceMap.get(indent)).append(fieldName).append("->insert(tempset);").append(NL);
							}else if(enumTypeMap.containsKey(setValueType0)){
								//枚举类型
								res.append(spaceMap.get(indent)).append("{").append(NL);
								indent++;
								res.append(spaceMap.get(indent)).append(setValueType0).append(" enumValue;").append(NL);
								//fillEnum(res, typeValue, fieldName, "enumKey");
								Map<String, Integer> enumMap = enumTypeMap.get(setValueType0);
								boolean nonfirst = false;
								for(Entry<String, Integer> entry1 : enumMap.entrySet()) {
									if(nonfirst) {
										res.append(" else ");
									}else {
										res.append(spaceMap.get(indent));
										nonfirst = true;
									}
									res.append("if(strcmp(setItem.value().get<string>().c_str(), \"").append(entry1.getKey()).append("\") == 0) {").append(NL);
									indent++;
									res.append(spaceMap.get(indent)).append("enumValue = ").append(entry1.getKey()).append(";").append(NL);
									decreaseIndent();
									res.append(spaceMap.get(indent)).append("}");
								}
								res.append(" else {").append(NL);
								indent++;
								res.append(spaceMap.get(indent)).append("printf(\"未知的枚举值: ").append(setValueType0).append("\\n\");").append(NL);
								decreaseIndent();
								res.append(spaceMap.get(indent)).append("}").append(NL);
								
								//将值加入第一层list
								res.append(spaceMap.get(indent)).append(fieldName).append("->insert(enumValue);").append(NL);
								decreaseIndent();
								res.append(spaceMap.get(indent)).append("}").append(NL);
							}else if(classTypeList.contains(setValueType0)){
								//自定义的类
								//fillClass(res, fieldType, fieldName, "mapItemValue");
								res.append(spaceMap.get(indent)).append(setValueType0).append(" *vinst = new ").append(setValueType0).append("();").append(NL);
								res.append(spaceMap.get(indent)).append("vinst->parseFromJson(setItem.value());").append(NL);
								//将值加入第一层list
								res.append(spaceMap.get(indent)).append(fieldName).append("->insert(vinst);").append(NL);
							}else {
								throw new Exception("未支持的数据类型set(2): " + setValueType0 + ", srcFileName=" + srcFileName+", className="+className);
							}
							decreaseIndent();
							res.append(spaceMap.get(indent)).append("}").append(NL).append(NL);
						}else if(enumTypeMap.containsKey(fieldType)){
							//枚举类型
							fillEnum(res, fieldType, fieldName, "jsonObj", false);
						}else if(classTypeList.contains(fieldType)){
							//自定义的类
							res.append(spaceMap.get(indent)).append(fieldName).append(" = new ").append(fieldType).append("();").append(NL);
							res.append(spaceMap.get(indent)).append(fieldName).append("->parseFromJson(jsonObj[\"").append(fieldName).append("\"]);").append(NL);
						}else {
							throw new Exception("未支持的数据类型(1): " + fieldType + ", srcFileName=" + srcFileName+", className="+className);
						}
						decreaseIndent();
						res.append(spaceMap.get(indent)).append("}").append(NL); //if end
					}
					res.append(spaceMap.get(indent)).append("return this;").append(NL);
					decreaseIndent();
					res.append(spaceMap.get(indent)).append("}").append(NL);
					
					//构建tojson字符串方法
					res.append(spaceMap.get(indent)).append("/** 注意: 返回结果需要free释放! */").append(NL);
					res.append(spaceMap.get(indent)).append("virtual char* toJsonString() {").append(NL);
					indent++;
					res.append(spaceMap.get(indent)).append("debug_printf(\"").append(className).append(" 构建json对象 \\n\");").append(NL);
					res.append(spaceMap.get(indent)).append("json jsonObj;").append(NL);
					for(Entry<String,String> entry : fieldMap.entrySet()) {
						String fieldName = entry.getKey();
						String fieldType = entry.getValue();
						res.append(spaceMap.get(indent)).append("debug_printf(\"").append(className).append(" 序列化字段: %s\\n \",\"").append(fieldName).append("\");").append(NL);
						if(cppBaseTypes.contains(fieldType)) {
							addBaseTypeToJson(res, fieldType, fieldName, "jsonObj");
						}else if(fieldType.startsWith("map")) {
							String typeKey = fieldType.substring(fieldType.indexOf('<')+1, fieldType.indexOf(','));
							String typeValue = fieldType.substring(fieldType.indexOf(',')+1, fieldType.length() - 1).trim();
							res.append(spaceMap.get(indent)).append("if(").append(fieldName).append(") {").append(NL); //if 开始
							indent++;
							res.append(spaceMap.get(indent)).append("for(auto &it : *").append(fieldName).append(") {").append(NL); //循环开始
							indent++;
							
							//key一定是原始类型
							res.append(spaceMap.get(indent)).append(convertToPtr2(typeKey, className)).append(" key = it.first;").append(NL);
							res.append(spaceMap.get(indent)).append(convertToPtr2(typeValue, className)).append(" value = it.second;").append(NL);
							//值可能是对象
							if(cppBaseTypes.contains(typeValue)) {
								//将值加入第一层map
								res.append(spaceMap.get(indent)).append("jsonObj[\"").append(fieldName).append("\"]").append("[key] = value;").append(NL);
							}else if(typeValue.startsWith("map")){
								//String typeKey2 = typeValue.substring(typeValue.indexOf('<')+1, typeValue.indexOf(','));
								String typeValue2 = typeValue.substring(typeValue.indexOf(',')+1, typeValue.length() - 1).trim();
								if(!cppBaseTypes.contains(typeValue2)) {
									throw new Exception("map最多嵌套两层! line="+lineOri);
								}
								res.append(spaceMap.get(indent)).append("json j_map(*value);").append(NL);
								res.append(spaceMap.get(indent)).append("jsonObj[\"").append(fieldName).append("\"]").append("[key] = j_map;").append(NL);
							}else if(typeValue.startsWith("vector")){
								String listValueType = typeValue.substring(typeValue.indexOf('<') + 1, typeValue.lastIndexOf('>'));
								if(!cppBaseTypes.contains(listValueType)) {
									throw new Exception("list最多嵌套两层! line="+lineOri+", className="+className);
								}
								res.append(spaceMap.get(indent)).append("json j_vector(*value);").append(NL);
								res.append(spaceMap.get(indent)).append("jsonObj[\"").append(fieldName).append("\"]").append("[key] = j_vector;").append(NL);
							}else if(typeValue.startsWith("set")){
								String setValueType = typeValue.substring(typeValue.indexOf('<') + 1, typeValue.lastIndexOf('>'));
								if(!cppBaseTypes.contains(setValueType)) {
									throw new Exception("set最多嵌套两层! line="+lineOri);
								}
								res.append(spaceMap.get(indent)).append("json j_set(*value);").append(NL);
								res.append(spaceMap.get(indent)).append("jsonObj[\"").append(fieldName).append("\"]").append("[key] = j_set;").append(NL);
							}else if(enumTypeMap.containsKey(typeValue)){
								//枚举类型
								res.append(spaceMap.get(indent)).append("string enumTarget;").append(NL);
								Map<String, Integer> enumMap = enumTypeMap.get(typeValue);
								boolean nonfirst = false;
								for(Entry<String, Integer> entry1 : enumMap.entrySet()) {
									if(nonfirst) {
										res.append(" else ");
									}else {
										res.append(spaceMap.get(indent));
										nonfirst = true;
									}
									res.append("if(value == ").append(entry1.getKey()).append(") {").append(NL);
									indent++;
									res.append(spaceMap.get(indent)).append("enumTarget = \"").append(entry1.getKey()).append("\";").append(NL);
									decreaseIndent();
									res.append(spaceMap.get(indent)).append("}");
								}
								res.append(" else {").append(NL);
								indent++;
								res.append(spaceMap.get(indent)).append("printf(\"未找到枚举值: %d \\n\", value);").append(NL); 
								decreaseIndent();
								res.append(spaceMap.get(indent)).append("}").append(NL);
								
								res.append(spaceMap.get(indent)).append("jsonObj[\"").append(fieldName).append("\"]").append("[key] = enumTarget;").append(NL);
							}else if(classTypeList.contains(typeValue)){
								//自定义的类
								res.append(spaceMap.get(indent)).append("jsonObj[\"").append(fieldName).append("\"]").append("[key] = json::parse(value->toJsonString());").append(NL);
							}else {
								throw new Exception("未支持的数据类型map(2): " + typeValue + ", srcFileName=" + srcFileName+", className="+className);
							}
							decreaseIndent();
							res.append(spaceMap.get(indent)).append("}").append(NL); //循环结束
							decreaseIndent();
							res.append(spaceMap.get(indent)).append("}").append(NL); //if结束
						}else if(fieldType.startsWith("vector") || fieldType.startsWith("set")) {
							String listValueType = fieldType.substring(fieldType.indexOf('<') + 1, fieldType.lastIndexOf('>'));
							res.append(spaceMap.get(indent)).append("if(").append(fieldName).append(") {").append(NL); //if 开始
							indent++;
							res.append(spaceMap.get(indent)).append("for(auto &it : *").append(fieldName).append(") {").append(NL); //循环开始
							indent++;
							//值可能是对象
							if(cppBaseTypes.contains(listValueType)) {
								res.append(spaceMap.get(indent)).append("jsonObj[\"").append(fieldName).append("\"]").append(".push_back(it);").append(NL);
							}else if(listValueType.startsWith("map")){
								//String typeKey2 = listValueType.substring(listValueType.indexOf('<')+1, listValueType.indexOf(','));
								String typeValue2 = listValueType.substring(listValueType.indexOf(',')+1, listValueType.length() - 1).trim();
								if(!cppBaseTypes.contains(typeValue2)) {
									throw new Exception("map最多嵌套两层! line="+lineOri);
								}
								res.append(spaceMap.get(indent)).append("json j_map(*it);").append(NL);
								res.append(spaceMap.get(indent)).append("jsonObj[\"").append(fieldName).append("\"]").append(".push_back(j_map);").append(NL);	
							}else if(listValueType.startsWith("vector")){
								String listValueType2 = listValueType.substring(listValueType.indexOf('<') + 1, listValueType.lastIndexOf('>'));
								if(!cppBaseTypes.contains(listValueType2)) {
									throw new Exception("list最多嵌套两层! line="+lineOri);
								}
								res.append(spaceMap.get(indent)).append("json j_vector(*it);").append(NL);
								res.append(spaceMap.get(indent)).append("jsonObj[\"").append(fieldName).append("\"]").append(".push_back(j_vector);").append(NL);
							}else if(listValueType.startsWith("set")){
								String setValueType = listValueType.substring(listValueType.indexOf('<') + 1, listValueType.lastIndexOf('>'));
								if(!cppBaseTypes.contains(setValueType)) {
									throw new Exception("set最多嵌套两层! line="+lineOri);
								}
								res.append(spaceMap.get(indent)).append("json j_set(*it);").append(NL);
								res.append(spaceMap.get(indent)).append("jsonObj[\"").append(fieldName).append("\"]").append(".push_back(j_set);").append(NL);
							}else if(enumTypeMap.containsKey(listValueType)){
								//枚举类型
								res.append(spaceMap.get(indent)).append("string enumTarget;").append(NL);
								Map<String, Integer> enumMap = enumTypeMap.get(listValueType);
								boolean nonfirst = false;
								for(Entry<String, Integer> entry1 : enumMap.entrySet()) {
									if(nonfirst) {
										res.append(" else ");
									}else {
										res.append(spaceMap.get(indent));
										nonfirst = true;
									}
									res.append("if(it == ").append(entry1.getKey()).append(") {").append(NL);
									indent++;
									res.append(spaceMap.get(indent)).append("enumTarget = \"").append(entry1.getKey()).append("\";").append(NL);
									decreaseIndent();
									res.append(spaceMap.get(indent)).append("}");
								}
								res.append(" else {").append(NL);
								indent++;
								res.append(spaceMap.get(indent)).append("printf(\"未找到枚举值: %d \\n\", it);").append(NL);
								decreaseIndent();
								res.append(spaceMap.get(indent)).append("}").append(NL);
								res.append(spaceMap.get(indent)).append("jsonObj[\"").append(fieldName).append("\"]").append(".push_back(enumTarget);").append(NL);
							}else if(classTypeList.contains(listValueType)){
								//自定义的类
								res.append(spaceMap.get(indent)).append("jsonObj[\"").append(fieldName).append("\"]").append(".push_back(json::parse(it->toJsonString()));").append(NL);
							}else {
								throw new Exception("未支持的数据类型list(2): " + listValueType + ", srcFileName=" + srcFileName+", className="+className);
							}
							decreaseIndent();
							res.append(spaceMap.get(indent)).append("}").append(NL); //end loop
							decreaseIndent();
							res.append(spaceMap.get(indent)).append("}").append(NL); //end if
						}else if(enumTypeMap.containsKey(fieldType)){
							//枚举类型
							res.append(spaceMap.get(indent)).append("{").append(NL); //block begin
							indent++;
							res.append(spaceMap.get(indent)).append("string enumTarget;").append(NL);
							Map<String, Integer> enumMap = enumTypeMap.get(fieldType);
							boolean nonfirst = false;
							for(Entry<String, Integer> entry1 : enumMap.entrySet()) {
								if(nonfirst) {
									res.append(" else ");
								}else {
									res.append(spaceMap.get(indent));
									nonfirst = true;
								}
								res.append("if(").append(fieldName).append(" == ").append(entry1.getKey()).append(") {").append(NL);
								indent++;
								res.append(spaceMap.get(indent)).append("enumTarget = \"").append(entry1.getKey()).append("\";").append(NL);
								decreaseIndent();
								res.append(spaceMap.get(indent)).append("}");
							}
							res.append(" else {").append(NL);
							indent++;
							res.append(spaceMap.get(indent)).append("printf(\"未找到枚举值: %d \\n\", ").append(fieldName).append(");").append(NL);
							decreaseIndent();
							res.append(spaceMap.get(indent)).append("}").append(NL);
							
							res.append(spaceMap.get(indent)).append("jsonObj[\"").append(fieldName).append("\"]").append(" = enumTarget;").append(NL);
							decreaseIndent();
							res.append(spaceMap.get(indent)).append("}").append(NL); //block end
						}else if(classTypeList.contains(fieldType)){
							//自定义的类
							res.append(spaceMap.get(indent)).append("if(").append(fieldName).append(") {").append(NL); //if 开始
							indent++;
							res.append(spaceMap.get(indent)).append("jsonObj[\"").append(fieldName).append("\"]").append(" = json::parse(").append(fieldName).append("->toJsonString());").append(NL);
							decreaseIndent();
							res.append(spaceMap.get(indent)).append("}").append(NL); //if 结束
						}else {
							throw new Exception("未支持的数据类型(1): " + fieldType + ", srcFileName=" + srcFileName+", className="+className);
						}
					}
					res.append(NL);
					res.append(spaceMap.get(indent)).append("debug_printf(\"").append(className).append(" 创建并输出字符串 \\n\");").append(NL);
					res.append(spaceMap.get(indent)).append("string jsonStr = jsonObj.dump();").append(NL);
					res.append(spaceMap.get(indent)).append("int jsonLen = jsonStr.length();").append(NL);
					res.append(spaceMap.get(indent)).append("char *res = (char*)malloc(jsonLen+1);").append(NL);
					res.append(spaceMap.get(indent)).append("if(!res) {").append(NL);
					indent++;
					res.append(spaceMap.get(indent)).append("printf(\"").append(className).append(" 内存不足, toJsonString失败!\\n\");").append(NL);
					res.append(spaceMap.get(indent)).append("return NULL;").append(NL);
					decreaseIndent();
					res.append(spaceMap.get(indent)).append("}").append(NL);
					res.append(spaceMap.get(indent)).append("memcpy(res, jsonStr.c_str(), jsonLen);").append(NL);
					res.append(spaceMap.get(indent)).append("res[jsonLen] = 0;").append(NL);
					res.append(spaceMap.get(indent)).append("return res;").append(NL);
					decreaseIndent();
					res.append(spaceMap.get(indent)).append("}").append(NL);
				} //end json function
				if(isClass) {
					//构造函数
					decreaseIndent();
					res.append(spaceMap.get(indent)).append("public:").append(NL);
					indent++;
					res.append(spaceMap.get(indent)).append(className).append("() {").append(NL);
					indent++;
					//res.append(spaceMap.get(indent)).append("//TODO, 在这里添加自定义对象初始化代码 ").append(NL);
					for(Entry<String,String> entry : fieldMap.entrySet()) {
						String fieldName = entry.getKey();
						String fieldType = entry.getValue();
						if(isDigitTypeInCpp(fieldType)) {
							res.append(spaceMap.get(indent)).append(fieldName).append(" = 0;").append(NL);
						}
					}
					decreaseIndent();
					res.append(spaceMap.get(indent)).append("}").append(NL);
					//析构函数
					res.append(spaceMap.get(indent)).append("virtual ~").append(className).append("() {").append(NL);
					indent++;
					res.append(spaceMap.get(indent)).append("// 开始释放对象 ").append(NL);
					for(Entry<String,String> entry : fieldMap.entrySet()) {
						String fieldName = entry.getKey();
						String fieldType = entry.getValue();
						if(cppBaseTypes.contains(fieldType) && fieldType.contains("*")) {
							res.append(spaceMap.get(indent)).append("//基础类型指针约定使用malloc初始化, free释放").append(NL);
							res.append(spaceMap.get(indent)).append("if(").append(fieldName).append(") {").append(NL);
							indent++;
							res.append(spaceMap.get(indent)).append("free(").append(fieldName).append(");").append(NL);
							decreaseIndent();
							res.append(spaceMap.get(indent)).append("}").append(NL);
						}else if(fieldType.startsWith("map")) {
							String typeValue = fieldType.substring(fieldType.indexOf(',')+1, fieldType.length() - 1).trim();
							res.append(spaceMap.get(indent)).append("if(").append(fieldName).append(") {").append(NL); //if 开始
							indent++;
							if((!cppBaseTypes.contains(typeValue) && !enumTypeMap.containsKey(typeValue)) || typeValue.contains("*")) {
								res.append(spaceMap.get(indent)).append("for(auto &it : *").append(fieldName).append(") {").append(NL); //循环开始
								indent++;
								res.append(spaceMap.get(indent)).append(convertToPtr2(typeValue, className)).append(" value = ")
								.append("it.second;").append(NL);
								//值可能是对象
								if(cppBaseTypes.contains(typeValue) && typeValue.contains("*")) {
									res.append(spaceMap.get(indent)).append("//基础类型指针约定使用malloc初始化, free释放").append(NL);
									res.append(spaceMap.get(indent)).append("free(value);").append(NL);
								}else if(typeValue.startsWith("map") || typeValue.startsWith("vector") || typeValue.startsWith("set")){
									String typeValue2 = (typeValue.startsWith("map") ? typeValue.substring(typeValue.indexOf(',')+1, typeValue.length() - 1).trim() : 
										typeValue.substring(typeValue.indexOf('<') + 1, typeValue.lastIndexOf('>')));
									if(!cppBaseTypes.contains(typeValue2)) {
										throw new Exception("map/list/set最多嵌套两层! line="+lineOri);
									}
									if(typeValue2.trim().endsWith("*")) {
										res.append(spaceMap.get(indent)).append("for(auto &subIt : *value) {").append(NL);
										indent++;
										res.append(spaceMap.get(indent)).append("//基础类型指针约定使用malloc初始化, free释放").append(NL);
										res.append(spaceMap.get(indent)).append("free(subIt);").append(NL);
										indent--;
										res.append(spaceMap.get(indent)).append("}").append(NL);
									}
									res.append(spaceMap.get(indent)).append("//对象类型指针约定使用new初始化, delete释放").append(NL);
									res.append(spaceMap.get(indent)).append("delete value;").append(NL);
								}else if(classTypeList.contains(typeValue)){
									//自定义的类
									res.append(spaceMap.get(indent)).append("//对象类型指针约定使用new初始化, delete释放").append(NL);
									res.append(spaceMap.get(indent)).append("delete value;").append(NL);
								}
								decreaseIndent();
								res.append(spaceMap.get(indent)).append("}").append(NL); //循环结束
							}
							res.append(spaceMap.get(indent)).append("//对象类型指针约定使用new初始化, delete释放").append(NL);
							res.append(spaceMap.get(indent)).append("delete ").append(fieldName).append(";").append(NL);
							decreaseIndent();
							res.append(spaceMap.get(indent)).append("}").append(NL); //if结束
						}else if(fieldType.startsWith("vector") || fieldType.startsWith("set")) {
							String listValueType = fieldType.substring(fieldType.indexOf('<') + 1, fieldType.lastIndexOf('>'));
							res.append(spaceMap.get(indent)).append("if(").append(fieldName).append(") {").append(NL); //if 开始
							indent++;
							if((!cppBaseTypes.contains(listValueType) && !enumTypeMap.containsKey(listValueType)) || listValueType.contains("*")) {
								res.append(spaceMap.get(indent)).append("for(auto &it : *").append(fieldName).append(") {").append(NL); //循环开始
								indent++;
								//值可能是对象
								if(cppBaseTypes.contains(listValueType) && listValueType.contains("*")) {
									res.append(spaceMap.get(indent)).append("//基础类型指针约定使用malloc初始化, free释放").append(NL);
									res.append(spaceMap.get(indent)).append("free(it);").append(NL);
								}else if(listValueType.startsWith("map") || listValueType.startsWith("vector") || listValueType.startsWith("set")){
									String typeValue2 = (listValueType.startsWith("map") ? listValueType.substring(listValueType.indexOf(',')+1, listValueType.length() - 1).trim() : 
										listValueType.substring(listValueType.indexOf('<') + 1, listValueType.lastIndexOf('>')));
									if(!cppBaseTypes.contains(typeValue2)) {
										throw new Exception("map/list/set最多嵌套两层! line="+lineOri);
									}
									if(typeValue2.trim().endsWith("*")) {
										res.append(spaceMap.get(indent)).append("for(auto &subIt : *it) {").append(NL);
										indent++;
										res.append(spaceMap.get(indent)).append("//基础类型指针约定使用malloc初始化, free释放").append(NL);
										res.append(spaceMap.get(indent)).append("free(subIt);").append(NL);
										indent--;
										res.append(spaceMap.get(indent)).append("}").append(NL);
									}
									res.append(spaceMap.get(indent)).append("//对象类型指针约定使用new初始化, delete释放").append(NL);
									res.append(spaceMap.get(indent)).append("delete it;").append(NL);
								}else if(classTypeList.contains(listValueType)){
									//自定义的类
									res.append(spaceMap.get(indent)).append("//对象类型指针约定使用new初始化, delete释放").append(NL);
									res.append(spaceMap.get(indent)).append("delete it;").append(NL);
								}
								decreaseIndent();
								res.append(spaceMap.get(indent)).append("}").append(NL); //循环结束
							}
							res.append(spaceMap.get(indent)).append("//对象类型指针约定使用new初始化, delete释放").append(NL);
							res.append(spaceMap.get(indent)).append("delete ").append(fieldName).append(";").append(NL);
							decreaseIndent();
							res.append(spaceMap.get(indent)).append("}").append(NL); //if结束
						}else if(classTypeList.contains(fieldType)){
							//自定义的类
							res.append(spaceMap.get(indent)).append("//对象类型指针约定使用new初始化, delete释放").append(NL);
							res.append(spaceMap.get(indent)).append("if(").append(fieldName).append(") {").append(NL);
							indent++;
							res.append(spaceMap.get(indent)).append("delete ").append(fieldName).append(";").append(NL);
							decreaseIndent();
							res.append(spaceMap.get(indent)).append("}").append(NL);
						}
					} //end field loop
					res.append(spaceMap.get(indent)).append("//TODO, 在这里添加额外的释放处理").append(NL);
					decreaseIndent();
					res.append(spaceMap.get(indent)).append("}").append(NL);
				} //end constructor and deconstructor
				decreaseIndent();
				res.append(spaceMap.get(indent)).append("};").append(NL);
				if(isClass) {
					classTypeList.add(className);
				}
				
				//添加测试数据
				if(isClass) {
					testIndent = 0;
					testRes.append(spaceMap.get(testIndent)).append("/** 返回对象指针 */").append(NL);
					testRes.append(spaceMap.get(testIndent)).append(className).append("* getClassInstanceOf").append(className).append("() {").append(NL);
					testIndent++;
					testRes.append(spaceMap.get(testIndent)).append(className).append(" *instance = new ")
					.append(className).append("();").append(NL);
					for(Entry<String,String> entry : fieldMap.entrySet()) {
						String fieldName = entry.getKey();
						String fieldType = entry.getValue(); 
						if(cppBaseTypes.contains(fieldType)) {
							if(this.isCharStrTypeInCpp(fieldType)){
								testRes.append(spaceMap.get(testIndent)).append("instance->").append(fieldName).append(" = (").append(fieldType).append(")malloc(48);").append(NL);
								testRes.append(spaceMap.get(testIndent)).append("strcpy((char*)instance->").append(fieldName).append(",\"").append(UUID.randomUUID().toString()).append("\");").append(NL);
							}else {
								testRes.append(spaceMap.get(testIndent)).append("instance->").append(fieldName).append(" = ").append(generateValueOfType(fieldType)).append(";").append(NL);
							}
						}else if(fieldType.startsWith("map")) {
							String typeKey = fieldType.substring(fieldType.indexOf('<')+1, fieldType.indexOf(','));
							String typeValue = fieldType.substring(fieldType.indexOf(',')+1, fieldType.length() - 1).trim();
							testRes.append(spaceMap.get(testIndent)).append("instance->").append(fieldName).append(" = new map<").append(typeKey).append(",")
							.append(convertToPtr2(typeValue, className)).append(">();").append(NL); 
							testRes.append(spaceMap.get(testIndent)).append("for(int i=0;i<2;i++) {").append(NL); //循环开始
							testIndent++;
							
							//key一定是原始类型
							if(this.isCharStrTypeInCpp(typeKey)){
								testRes.append(spaceMap.get(testIndent)).append(typeKey).append(" key = (").append(typeKey).append(")malloc(48);").append(NL);
								testRes.append(spaceMap.get(testIndent)).append(typeKey).append("strcpy((char*)key,\"").append(UUID.randomUUID().toString()).append("\");").append(NL);
							}else {
								testRes.append(spaceMap.get(testIndent)).append(typeKey).append(" key = ").append(generateValueOfType(typeKey)).append(";").append(NL);
							}
							//值可能是对象
							if(cppBaseTypes.contains(typeValue)) {
								if(this.isCharStrTypeInCpp(typeValue)){
									testRes.append(spaceMap.get(testIndent)).append(typeValue).append(" value = (").append(typeValue).append(")malloc(48);").append(NL);
									testRes.append(spaceMap.get(testIndent)).append(typeValue).append("strcpy((char*)value,\"").append(UUID.randomUUID().toString()).append("\");").append(NL);
								}else {
									testRes.append(spaceMap.get(testIndent)).append(typeValue).append(" value = ").append(generateValueOfType(typeValue)).append(";").append(NL);
								}
								//加入第一层map
								testRes.append(spaceMap.get(testIndent)).append("instance->").append(fieldName).append("->insert(pair<").append(typeKey).append(',').append(typeValue).append(">(")
								.append("key, value));").append(NL);
							}else if(typeValue.startsWith("map")){
								String typeKey2 = typeValue.substring(typeValue.indexOf('<')+1, typeValue.indexOf(','));
								String typeValue2 = typeValue.substring(typeValue.indexOf(',')+1, typeValue.length() - 1).trim();
								if(!cppBaseTypes.contains(typeValue2)) {
									throw new Exception("map最多嵌套两层! line="+lineOri);
								}
								testRes.append(spaceMap.get(testIndent)).append("map<").append(typeKey2).append(",").append(convertToPtr2(typeValue2, className)).append("> *value = new map<").append(typeKey2).append(",")
								.append(convertToPtr2(typeValue2, className)).append(">();").append(NL); //if 开始
								testRes.append(spaceMap.get(testIndent)).append("for(int j=0;j<2;j++) {").append(NL); //循环开始
								testIndent++;
								
								//生成key
								if(this.isCharStrTypeInCpp(typeKey2)){
									testRes.append(spaceMap.get(testIndent)).append(typeKey2).append(" key2 = (").append(typeKey2).append(")malloc(48);").append(NL);
									testRes.append(spaceMap.get(testIndent)).append(typeKey2).append("strcpy((char*)key2, \"").append(UUID.randomUUID().toString()).append("\");").append(NL);
								}else {
									testRes.append(spaceMap.get(testIndent)).append(typeKey2).append(" key2 = ").append(generateValueOfType(typeKey2)).append(";").append(NL);
								}
								//生成value
								if(this.isCharStrTypeInCpp(typeValue2)){
									testRes.append(spaceMap.get(testIndent)).append(typeValue2).append(" value2 = (").append(typeValue2).append(")malloc(48);").append(NL);
									testRes.append(spaceMap.get(testIndent)).append(typeValue2).append("strcpy((char*)value2, \"").append(UUID.randomUUID().toString()).append("\");").append(NL);
								}else {
									testRes.append(spaceMap.get(testIndent)).append(typeValue2).append(" value2 = ").append(generateValueOfType(typeValue2)).append(";").append(NL);
								}
								//加入子map
								testRes.append(spaceMap.get(testIndent)).append("value->insert(pair<").append(typeKey2).append(',').append(typeValue2).append(">(")
								.append("key2, value2));").append(NL);
								
								testIndent--;
								testRes.append(spaceMap.get(testIndent)).append("}").append(NL); //循环
								
								//加入第一层map
								testRes.append(spaceMap.get(testIndent)).append("instance->").append(fieldName).append("->insert(pair<").append(typeKey).append(',').append(convertToPtr2(typeValue, className)).append(">(")
								.append("key, value));").append(NL);
							}else if(typeValue.startsWith("vector")){
								String listValueType = typeValue.substring(typeValue.indexOf('<') + 1, typeValue.lastIndexOf('>'));
								if(!cppBaseTypes.contains(listValueType)) {
									throw new Exception("list最多嵌套两层! line="+lineOri+", className="+className);
								}
								testRes.append(spaceMap.get(testIndent)).append("vector<").append(convertToPtr2(listValueType, className)).append("> *value = new vector<").append(convertToPtr2(listValueType, className))
								.append(">();").append(NL); //if 开始
								testRes.append(spaceMap.get(testIndent)).append("for(int j=0;j<2;j++) {").append(NL); //循环开始
								testIndent++;
								
								//生成value
								if(this.isCharStrTypeInCpp(listValueType)){
									testRes.append(spaceMap.get(testIndent)).append(listValueType).append(" value2 = (").append(listValueType).append(")malloc(48);").append(NL);
									testRes.append(spaceMap.get(testIndent)).append(listValueType).append("strcpy((char*)value2, \"").append(UUID.randomUUID().toString()).append("\");").append(NL);
								}else {
									testRes.append(spaceMap.get(testIndent)).append(listValueType).append(" value2 = ").append(generateValueOfType(listValueType)).append(";").append(NL);
								}
								//加入子vector
								testRes.append(spaceMap.get(testIndent)).append("value->push_back(value2);").append(NL);
								
								testIndent--;
								testRes.append(spaceMap.get(testIndent)).append("}").append(NL); //循环开始
								
								//加入第一层map
								testRes.append(spaceMap.get(testIndent)).append("instance->").append(fieldName).append("->insert(pair<").append(typeKey).append(',').append(convertToPtr2(typeValue, className)).append(">(")
								.append("key, value));").append(NL);
							}else if(typeValue.startsWith("set")){
								String setValueType = typeValue.substring(typeValue.indexOf('<') + 1, typeValue.lastIndexOf('>'));
								if(!cppBaseTypes.contains(setValueType)) {
									throw new Exception("set最多嵌套两层! line="+lineOri);
								}
								testRes.append(spaceMap.get(testIndent)).append("set<").append(convertToPtr2(setValueType, className)).append("> *value = new set<").append(convertToPtr2(setValueType, className))
								.append(">();").append(NL); //if 开始
								testRes.append(spaceMap.get(testIndent)).append("for(int j=0;j<2;j++) {").append(NL); //循环开始
								testIndent++;
								
								//生成value
								if(this.isCharStrTypeInCpp(setValueType)){
									testRes.append(spaceMap.get(testIndent)).append(setValueType).append(" value2 = (").append(setValueType).append(")malloc(48);").append(NL);
									testRes.append(spaceMap.get(testIndent)).append(setValueType).append("strcpy((char*)value2, \"").append(UUID.randomUUID().toString()).append("\");").append(NL);
								}else {
									testRes.append(spaceMap.get(testIndent)).append(setValueType).append(" value2 = ").append(generateValueOfType(setValueType)).append(";").append(NL);
								}
								//加入子vector
								testRes.append(spaceMap.get(testIndent)).append("value->insert(value2);").append(NL);
								
								testIndent--;
								testRes.append(spaceMap.get(testIndent)).append("}").append(NL); //循环开始
								
								//加入第一层map
								testRes.append(spaceMap.get(testIndent)).append("instance->").append(fieldName).append("->insert(pair<").append(typeKey).append(',').append(convertToPtr2(typeValue, className)).append(">(")
								.append("key, value));").append(NL);
							}else if(enumTypeMap.containsKey(typeValue)){
								//枚举类型
								Map<String, Integer> enumMap = enumTypeMap.get(typeValue);
								testRes.append(spaceMap.get(testIndent)).append(typeValue).append(" value = ").append(enumMap.keySet().iterator().next()).append(";").append(NL);
								
								//加入第一层map
								testRes.append(spaceMap.get(testIndent)).append("instance->").append(fieldName).append("->insert(pair<").append(typeKey).append(',').append(convertToPtr2(typeValue, className)).append(">(")
								.append("key, value));").append(NL);
							}else if(classTypeList.contains(typeValue)){
								//自定义的类
								testRes.append(spaceMap.get(testIndent)).append(typeValue).append(" *value = getClassInstanceOf").append(typeValue).append("();").append(NL);
								
								//加入第一层map
								testRes.append(spaceMap.get(testIndent)).append("instance->").append(fieldName).append("->insert(pair<").append(typeKey).append(',').append(convertToPtr2(typeValue, className)).append(">(")
								.append("key, value));").append(NL);
							}else {
								throw new Exception("未支持的数据类型map(2): " + typeValue + ", srcFileName=" + srcFileName+", className="+className);
							}
							testIndent--;
							testRes.append(spaceMap.get(testIndent)).append("}").append(NL); //循环结束
						}else if(fieldType.startsWith("vector")) {
							String listValueType = fieldType.substring(fieldType.indexOf('<') + 1, fieldType.lastIndexOf('>'));
							testRes.append(spaceMap.get(testIndent)).append("instance->").append(fieldName).append(" = new vector<").append(convertToPtr2(listValueType, className)).append(">();").append(NL); 
							testRes.append(spaceMap.get(testIndent)).append("for(int i=0;i<2;i++) {").append(NL); //循环开始
							testIndent++;
							
							//值可能是对象
							if(cppBaseTypes.contains(listValueType)) {
								if(this.isCharStrTypeInCpp(listValueType)){
									testRes.append(spaceMap.get(testIndent)).append(listValueType).append(" value = (").append(listValueType).append(")malloc(48);").append(NL);
									testRes.append(spaceMap.get(testIndent)).append(listValueType).append("strcpy((char*)value, \"").append(UUID.randomUUID().toString()).append("\");").append(NL);
								}else {
									testRes.append(spaceMap.get(testIndent)).append(listValueType).append(" value = ").append(generateValueOfType(listValueType)).append(";").append(NL);
								}
								//将值加入第一层vector/set
								testRes.append(spaceMap.get(testIndent)).append("instance->").append(fieldName).append("->push_back(").append("value);").append(NL);
							}else if(listValueType.startsWith("map")){
								String typeKey2 = listValueType.substring(listValueType.indexOf('<')+1, listValueType.indexOf(','));
								String typeValue2 = listValueType.substring(listValueType.indexOf(',')+1, listValueType.length() - 1).trim();
								if(!cppBaseTypes.contains(typeValue2)) {
									throw new Exception("map最多嵌套两层! line="+lineOri);
								}
								testRes.append(spaceMap.get(testIndent)).append("map<").append(typeKey2).append(",").append(convertToPtr2(typeValue2, className)).append("> *value = new map<").append(typeKey2).append(",")
								.append(convertToPtr2(typeValue2, className)).append(">();").append(NL); //if 开始
								testRes.append(spaceMap.get(testIndent)).append("for(int j=0;j<2;j++) {").append(NL); //循环开始
								testIndent++;
								
								//生成key
								if(this.isCharStrTypeInCpp(typeKey2)){
									testRes.append(spaceMap.get(testIndent)).append(typeKey2).append(" key2 = (").append(typeKey2).append(")malloc(48);").append(NL);
									testRes.append(spaceMap.get(testIndent)).append(typeKey2).append("strcpy((char*)key2, \"").append(UUID.randomUUID().toString()).append("\");").append(NL);
								}else {
									testRes.append(spaceMap.get(testIndent)).append(typeKey2).append(" key2 = ").append(generateValueOfType(typeKey2)).append(";").append(NL);
								}
								//生成value
								if(this.isCharStrTypeInCpp(typeValue2)){
									testRes.append(spaceMap.get(testIndent)).append(typeValue2).append(" value2 = (").append(typeValue2).append(")malloc(48);").append(NL);
									testRes.append(spaceMap.get(testIndent)).append(typeValue2).append("strcpy((char*)value2, \"").append(UUID.randomUUID().toString()).append("\");").append(NL);
								}else {
									testRes.append(spaceMap.get(testIndent)).append(typeValue2).append(" value2 = ").append(generateValueOfType(typeValue2)).append(";").append(NL);
								}
								//加入子map
								testRes.append(spaceMap.get(testIndent)).append("value->insert(pair<").append(typeKey2).append(',').append(convertToPtr2(typeValue2, className)).append(">(")
								.append("key2, value2));").append(NL);
								
								testIndent--;
								testRes.append(spaceMap.get(testIndent)).append("}").append(NL); //循环开始
								
								//将值加入第一层vector/set
								testRes.append(spaceMap.get(testIndent)).append("instance->").append(fieldName).append("->push_back(").append("value);").append(NL);
							}else if(listValueType.startsWith("vector")){
								String listValueType2 = listValueType.substring(listValueType.indexOf('<') + 1, listValueType.lastIndexOf('>'));
								if(!cppBaseTypes.contains(listValueType2)) {
									throw new Exception("list最多嵌套两层! line="+lineOri+", className="+className);
								}
								testRes.append(spaceMap.get(testIndent)).append("vector<").append(convertToPtr2(listValueType2, className)).append("> *value = new vector<").append(convertToPtr2(listValueType2, className))
								.append(">();").append(NL);
								testRes.append(spaceMap.get(testIndent)).append("for(int j=0;j<2;j++) {").append(NL); //循环开始
								testIndent++;
								
								//生成value
								if(this.isCharStrTypeInCpp(listValueType2)){
									testRes.append(spaceMap.get(testIndent)).append(listValueType2).append(" value2 = (").append(listValueType2).append(")malloc(48);").append(NL);
									testRes.append(spaceMap.get(testIndent)).append(listValueType2).append("strcpy((char*)value2, \"").append(UUID.randomUUID().toString()).append("\");").append(NL);
								}else {
									testRes.append(spaceMap.get(testIndent)).append(listValueType2).append(" value2 = ").append(generateValueOfType(listValueType2)).append(";").append(NL);
								}
								//加入子vector
								testRes.append(spaceMap.get(testIndent)).append("value->push_back(value2);").append(NL);
								
								testIndent--;
								testRes.append(spaceMap.get(testIndent)).append("}").append(NL); //循环开始
								
								//将值加入第一层vector/set
								testRes.append(spaceMap.get(testIndent)).append("instance->").append(fieldName).append("->push_back(").append("value);").append(NL);
							}else if(listValueType.startsWith("set")){
								String setValueType = listValueType.substring(listValueType.indexOf('<') + 1, listValueType.lastIndexOf('>'));
								if(!cppBaseTypes.contains(setValueType)) {
									throw new Exception("set最多嵌套两层! line="+lineOri);
								}
								testRes.append(spaceMap.get(testIndent)).append("set<").append(convertToPtr2(setValueType, className)).append("> *value = new set<").append(convertToPtr2(setValueType, className))
								.append(">();").append(NL); //if 开始
								testRes.append(spaceMap.get(testIndent)).append("for(int j=0;j<2;j++) {").append(NL); //循环开始
								testIndent++;
								
								//生成value
								if(this.isCharStrTypeInCpp(setValueType)){
									testRes.append(spaceMap.get(testIndent)).append(setValueType).append(" value2 = (").append(setValueType).append(")malloc(48);").append(NL);
									testRes.append(spaceMap.get(testIndent)).append(setValueType).append("strcpy((char*)value2, \"").append(UUID.randomUUID().toString()).append("\");").append(NL);
								}else {
									testRes.append(spaceMap.get(testIndent)).append(setValueType).append(" value2 = ").append(generateValueOfType(setValueType)).append(";").append(NL);
								}
								//加入子vector
								testRes.append(spaceMap.get(testIndent)).append("value->insert(value2);").append(NL);
								
								testIndent--;
								testRes.append(spaceMap.get(testIndent)).append("}").append(NL); //循环开始
								
								//将值加入第一层vector/set
								testRes.append(spaceMap.get(testIndent)).append("instance->").append(fieldName).append("->push_back(").append("value);").append(NL);
							}else if(enumTypeMap.containsKey(listValueType)){
								//枚举类型
								Map<String, Integer> enumMap = enumTypeMap.get(listValueType);
								testRes.append(spaceMap.get(testIndent)).append(listValueType).append(" value = ").append(enumMap.keySet().iterator().next()).append(";").append(NL);
								
								//将值加入第一层vector/set
								testRes.append(spaceMap.get(testIndent)).append("instance->").append(fieldName).append("->push_back(").append("value);").append(NL);
							}else if(classTypeList.contains(listValueType)){
								//自定义的类
								testRes.append(spaceMap.get(testIndent)).append(listValueType).append(" *value = getClassInstanceOf").append(listValueType).append("();").append(NL);
								
								//将值加入第一层vector/set
								testRes.append(spaceMap.get(testIndent)).append("instance->").append(fieldName).append("->push_back(").append("value);").append(NL);
							}else {
								throw new Exception("未支持的数据类型map(2): " + listValueType + ", srcFileName=" + srcFileName+", className="+className);
							}
							
							testIndent--;
							testRes.append(spaceMap.get(testIndent)).append("}").append(NL); //循环结束
						}else if(fieldType.startsWith("set")) {
							String setValueType = fieldType.substring(fieldType.indexOf('<') + 1, fieldType.lastIndexOf('>'));
							testRes.append(spaceMap.get(testIndent)).append("instance->").append(fieldName).append(" = new set<").append(convertToPtr2(setValueType, className)).append(">();").append(NL); 
							testRes.append(spaceMap.get(testIndent)).append("for(int i=0;i<2;i++) {").append(NL); //循环开始
							testIndent++;
							
							//值可能是对象
							if(cppBaseTypes.contains(setValueType)) {
								if(this.isCharStrTypeInCpp(setValueType)){
									testRes.append(spaceMap.get(testIndent)).append(setValueType).append(" value = (").append(setValueType).append(")malloc(48);").append(NL);
									testRes.append(spaceMap.get(testIndent)).append(setValueType).append("strcpy((char*)value, \"").append(UUID.randomUUID().toString()).append("\");").append(NL);
								}else {
									testRes.append(spaceMap.get(testIndent)).append(setValueType).append(" value = ").append(generateValueOfType(setValueType)).append(";").append(NL);
								}
								//将值加入第一层vector/set
								testRes.append(spaceMap.get(testIndent)).append("instance->").append(fieldName).append("->insert(").append("value);").append(NL);
							}else if(setValueType.startsWith("map")){
								String typeKey2 = setValueType.substring(setValueType.indexOf('<')+1, setValueType.indexOf(','));
								String typeValue2 = setValueType.substring(setValueType.indexOf(',')+1, setValueType.length() - 1).trim();
								if(!cppBaseTypes.contains(typeValue2)) {
									throw new Exception("map最多嵌套两层! line="+lineOri);
								}
								testRes.append(spaceMap.get(testIndent)).append("map<").append(typeKey2).append(",").append(convertToPtr2(typeValue2, className)).append("> *value = new map<").append(typeKey2).append(",")
								.append(convertToPtr2(typeValue2, className)).append(">();").append(NL); //if 开始
								testRes.append(spaceMap.get(testIndent)).append("for(int j=0;j<2;j++) {").append(NL); //循环开始
								testIndent++;
								
								//生成key
								if(this.isCharStrTypeInCpp(typeKey2)){
									testRes.append(spaceMap.get(testIndent)).append(typeKey2).append(" key2 = (").append(typeKey2).append(")malloc(48);").append(NL);
									testRes.append(spaceMap.get(testIndent)).append(typeKey2).append("strcpy((char*)key2, \"").append(UUID.randomUUID().toString()).append("\");").append(NL);
								}else {
									testRes.append(spaceMap.get(testIndent)).append(typeKey2).append(" key2 = ").append(generateValueOfType(typeKey2)).append(";").append(NL);
								}
								//生成value
								if(this.isCharStrTypeInCpp(typeValue2)){
									testRes.append(spaceMap.get(testIndent)).append(typeValue2).append(" value2 = (").append(typeValue2).append(")malloc(48);").append(NL);
									testRes.append(spaceMap.get(testIndent)).append(typeValue2).append("strcpy((char*)value2, \"").append(UUID.randomUUID().toString()).append("\");").append(NL);
								}else {
									testRes.append(spaceMap.get(testIndent)).append(typeValue2).append(" value2 = ").append(generateValueOfType(typeValue2)).append(";").append(NL);
								}
								//加入子map
								testRes.append(spaceMap.get(testIndent)).append("value->insert(pair<").append(typeKey2).append(',').append(convertToPtr2(typeValue2, className)).append(">(")
								.append("key2, value2));").append(NL);
								
								testIndent--;
								testRes.append(spaceMap.get(testIndent)).append("}").append(NL); //循环开始
								
								//将值加入第一层vector/set
								testRes.append(spaceMap.get(testIndent)).append("instance->").append(fieldName).append("->insert(").append("value);").append(NL);
							}else if(setValueType.startsWith("vector")){
								String listValueType2 = setValueType.substring(setValueType.indexOf('<') + 1, setValueType.lastIndexOf('>'));
								if(!cppBaseTypes.contains(listValueType2)) {
									throw new Exception("list最多嵌套两层! line="+lineOri+", className="+className);
								}
								testRes.append(spaceMap.get(testIndent)).append("vector<").append(convertToPtr2(listValueType2, className)).append("> *value = new vector<").append(convertToPtr2(listValueType2, className))
								.append(">();").append(NL);
								testRes.append(spaceMap.get(testIndent)).append("for(int j=0;j<2;j++) {").append(NL); //循环开始
								testIndent++;
								
								//生成value
								if(this.isCharStrTypeInCpp(listValueType2)){
									testRes.append(spaceMap.get(testIndent)).append(listValueType2).append(" value2 = (").append(listValueType2).append(")malloc(48);").append(NL);
									testRes.append(spaceMap.get(testIndent)).append(listValueType2).append("strcpy((char*)value2, \"").append(UUID.randomUUID().toString()).append("\");").append(NL);
								}else {
									testRes.append(spaceMap.get(testIndent)).append(listValueType2).append(" value2 = ").append(generateValueOfType(listValueType2)).append(";").append(NL);
								}
								//加入子vector
								testRes.append(spaceMap.get(testIndent)).append("value->push_back(value2);").append(NL);
								
								testIndent--;
								testRes.append(spaceMap.get(testIndent)).append("}").append(NL); //循环开始
								
								//将值加入第一层vector/set
								testRes.append(spaceMap.get(testIndent)).append("instance->").append(fieldName).append("->insert(").append("value);").append(NL);
							}else if(setValueType.startsWith("set")){
								String setValueType2 = setValueType.substring(setValueType.indexOf('<') + 1, setValueType.lastIndexOf('>'));
								if(!cppBaseTypes.contains(setValueType2)) {
									throw new Exception("set最多嵌套两层! line="+lineOri);
								}
								testRes.append(spaceMap.get(testIndent)).append("set<").append(convertToPtr2(setValueType2, className)).append("> *value = new set<").append(convertToPtr2(setValueType2, className))
								.append(">();").append(NL); //if 开始
								testRes.append(spaceMap.get(testIndent)).append("for(int j=0;j<2;j++) {").append(NL); //循环开始
								testIndent++;
								
								//生成value
								if(this.isCharStrTypeInCpp(setValueType2)){
									testRes.append(spaceMap.get(testIndent)).append(setValueType2).append(" value2 = (").append(setValueType2).append(")malloc(48);").append(NL);
									testRes.append(spaceMap.get(testIndent)).append(setValueType2).append("strcpy((char*)value2, \"").append(UUID.randomUUID().toString()).append("\");").append(NL);
								}else {
									testRes.append(spaceMap.get(testIndent)).append(setValueType2).append(" value2 = ").append(generateValueOfType(setValueType2)).append(";").append(NL);
								}
								//加入子vector
								testRes.append(spaceMap.get(testIndent)).append("value->insert(value2);").append(NL);
								
								testIndent--;
								testRes.append(spaceMap.get(testIndent)).append("}").append(NL); //循环开始
								
								//将值加入第一层vector/set
								testRes.append(spaceMap.get(testIndent)).append("instance->").append(fieldName).append("->insert(").append("value);").append(NL);
							}else if(enumTypeMap.containsKey(setValueType)){
								//枚举类型
								Map<String, Integer> enumMap = enumTypeMap.get(setValueType);
								testRes.append(spaceMap.get(testIndent)).append(setValueType).append(" value = ").append(enumMap.keySet().iterator().next()).append(";").append(NL);
								
								//将值加入第一层vector/set
								testRes.append(spaceMap.get(testIndent)).append("instance->").append(fieldName).append("->insert(").append("value);").append(NL);
							}else if(classTypeList.contains(setValueType)){
								//自定义的类
								testRes.append(spaceMap.get(testIndent)).append(setValueType).append(" *value = getClassInstanceOf").append(setValueType).append("();").append(NL);
								
								//将值加入第一层vector/set
								testRes.append(spaceMap.get(testIndent)).append("instance->").append(fieldName).append("->insert(").append("value);").append(NL);
							}else {
								throw new Exception("未支持的数据类型map(2): " + setValueType + ", srcFileName=" + srcFileName+", className="+className);
							}
							
							testIndent--;
							testRes.append(spaceMap.get(testIndent)).append("}").append(NL); //循环结束
						}else if(enumTypeMap.containsKey(fieldType)){
							//枚举类型
							Map<String, Integer> enumMap = enumTypeMap.get(fieldType);
							testRes.append(spaceMap.get(testIndent)).append("instance->").append(fieldName).append(" = ").append(enumMap.keySet().iterator().next()).append(";").append(NL);
						}else if(classTypeList.contains(fieldType)){
							//自定义的类
							testRes.append(spaceMap.get(testIndent)).append("instance->").append(fieldName).append(" = getClassInstanceOf").append(fieldType).append("();").append(NL);
						}else {
							throw new Exception("未支持的数据类型(1): " + fieldType + ", srcFileName=" + srcFileName+", className="+className);
						}
					} //end for test
					testRes.append(NL);
					testRes.append(spaceMap.get(testIndent)).append("return instance;").append(NL); //end test class
					testIndent--;
					testRes.append(spaceMap.get(testIndent)).append("}").append(NL); //end test class
					testRes.append(NL);
				} //end if test
				
				isEnum = false;
				isClass = false;
				fieldMap.clear();
				continue;
			}
			//其它的都添加上
			res.append(spaceMap.get(indent)).append(lineOri.trim()).append(NL);
		}
		decreaseIndent();
		res.append(NL);
		res.append(spaceMap.get(indent)).append("} ").append(NL);
		
		File targetFile = new File(targetFilePath);
		if(targetFile.exists()) {
			targetFile.delete();
		}
		FileUtils.write(targetFile, res.toString(), StandardCharsets.UTF_8);
		
		return true;
	}
	
	final void addTestEntranceAndWriteFile(String targetFilePath) throws Exception {
		testRes.append(NL);
		testRes.append(spaceMap.get(testIndent)).append("/** 测试入口 */").append(NL); 
		testRes.append(spaceMap.get(testIndent)).append("int main(int argc, char **argv) {").append(NL); 
		testIndent++;
		testRes.append(spaceMap.get(testIndent)).append("int okCnt = 0, failCnt = 0;").append(NL).append(NL); 
		
		for(String className : classTypeList) {
			String instanceName = "instance" + className;
			String jsonString = "jsonStr"+className;
			String json = "json" + className;
			String instanceName2 = "instance" + className+"2";
			String jsonString2 = "jsonStr"+className+"2";
			testRes.append(spaceMap.get(testIndent)).append("try {").append(NL);
			testIndent++;
			testRes.append(spaceMap.get(testIndent)).append("// 测试 ").append(className).append(NL);
			testRes.append(spaceMap.get(testIndent)).append(className).append(" *").append(instanceName)
			.append(" = getClassInstanceOf").append(className).append("();").append(NL);
			
			testRes.append(spaceMap.get(testIndent)).append("// json序列化 ").append(NL);
			testRes.append(spaceMap.get(testIndent)).append("char* ").append(jsonString)
			.append(" = ").append(instanceName).append("->toJsonString();").append(NL);
			testRes.append(spaceMap.get(testIndent)).append("printf(\"").append(className).append(".toJsonString=%s \\n\", ").append(jsonString).append(");").append(NL);
			
			testRes.append(spaceMap.get(testIndent)).append("// json反序列化1 ").append(NL);
			testRes.append(spaceMap.get(testIndent)).append("json ").append(json).append(" = json::parse(").append(jsonString).append(");").append(NL);
			testRes.append(spaceMap.get(testIndent)).append("printf(\"").append(className).append(".jsonDumpStr=%s \\n\", ").append(json).append(".dump().c_str());").append(NL);
			
			testRes.append(spaceMap.get(testIndent)).append("// json反序列化2 ").append(NL);
			testRes.append(spaceMap.get(testIndent)).append(className).append(" *").append(instanceName2)
			.append(" = new ").append(className).append("();").append(NL);
			testRes.append(spaceMap.get(testIndent)).append("printf(\"parsing from json \\n\");").append(NL);
			testRes.append(spaceMap.get(testIndent)).append(instanceName2).append("->parseFromJson(").append(json).append(");").append(NL);
			testRes.append(spaceMap.get(testIndent)).append("printf(\"parsing over \\n\");").append(NL);
			testRes.append(spaceMap.get(testIndent)).append("char* ").append(jsonString2).append(" = ").append(instanceName2).append("->toJsonString();").append(NL);
			testRes.append(spaceMap.get(testIndent)).append("printf(\"").append(className).append(".toJsonString=%s \\n\", ").append(jsonString2).append(");").append(NL);
			
			testRes.append(spaceMap.get(testIndent)).append("// 正确性比较 ").append(NL);
			testRes.append(spaceMap.get(testIndent)).append("if(strcmp(").append(jsonString).append(",").append(jsonString2).append(") == 0) {").append(NL);
			testIndent++;
			testRes.append(spaceMap.get(testIndent)).append("okCnt++;").append(NL);
			testRes.append(spaceMap.get(testIndent)).append("printf(\"OK     ").append(className).append(" \\n\\n\");").append(NL);
			testIndent--;
			testRes.append(spaceMap.get(testIndent)).append("} else {").append(NL);
			testIndent++;
			testRes.append(spaceMap.get(testIndent)).append("failCnt++;").append(NL);
			testRes.append(spaceMap.get(testIndent)).append("printf(\"Failed ").append(className).append(" \\n\\n\");").append(NL);
			testIndent--;
			testRes.append(spaceMap.get(testIndent)).append("}").append(NL);
			
			testRes.append(spaceMap.get(testIndent)).append("// 释放资源 ").append(NL);
			testRes.append(spaceMap.get(testIndent)).append("free(").append(jsonString2).append(");").append(NL);
			testRes.append(spaceMap.get(testIndent)).append(jsonString2).append(" = NULL;").append(NL);
			testRes.append(spaceMap.get(testIndent)).append("delete ").append(instanceName2).append(";").append(NL);
			testRes.append(spaceMap.get(testIndent)).append(instanceName2).append(" = NULL;").append(NL);
			testRes.append(spaceMap.get(testIndent)).append("free(").append(jsonString).append(");").append(NL);
			testRes.append(spaceMap.get(testIndent)).append(jsonString).append(" = NULL;").append(NL);
			testRes.append(spaceMap.get(testIndent)).append("delete ").append(instanceName).append(";").append(NL);
			testRes.append(spaceMap.get(testIndent)).append(instanceName).append(" = NULL;").append(NL);
			testRes.append(spaceMap.get(testIndent)).append("//release over ").append(NL);
			testRes.append(NL);
			
			testIndent--;
			testRes.append(spaceMap.get(testIndent)).append("} catch(const json::parse_error& e){").append(NL);
			testIndent++; 
			testRes.append(spaceMap.get(testIndent)).append("printf(\"JSON解析异常: %s, \\n测试类名:%s \\n\", e.what(), \"").append(className).append("\");").append(NL);
			testRes.append(spaceMap.get(testIndent)).append("exit(-1);").append(NL);
			testIndent--;
			testRes.append(spaceMap.get(testIndent)).append("} catch(exception& e){").append(NL);
			testIndent++; 
			testRes.append(spaceMap.get(testIndent)).append("printf(\"处理异常: %s, \\n测试类名:%s \\n\", e.what(), \"").append(className).append("\");").append(NL);
			testRes.append(spaceMap.get(testIndent)).append("exit(-1);").append(NL);
			testIndent--;
			testRes.append(spaceMap.get(testIndent)).append("} catch(...){").append(NL);
			testIndent++; 
			testRes.append(spaceMap.get(testIndent)).append("printf(\"处理异常: 未知的异常, \\n测试类名:%s \\n\", \"").append(className).append("\");").append(NL);
			testRes.append(spaceMap.get(testIndent)).append("exit(-1);").append(NL);
			testIndent--;
			testRes.append(spaceMap.get(testIndent)).append("}").append(NL);
		}
		
		testRes.append(NL);
		testRes.append(spaceMap.get(testIndent)).append("printf(\"OK count: %d, Fail count: %d \\n\", okCnt, failCnt);").append(NL);
		testRes.append(spaceMap.get(testIndent)).append("return 0;").append(NL);
		
		testIndent--;
		testRes.append(spaceMap.get(testIndent)).append("}").append(NL); 
		testRes.append(NL);
		
		StringBuilder header = new StringBuilder();
		addVersion(header);
		header.append(NL);
		header.append("#include <stdio.h>").append(NL);
		header.append("#include <stdlib.h>").append(NL);
		header.append("#include <string>").append(NL);
		header.append("#include <algorithm>").append(NL);
		header.append("#include <array>").append(NL);
		header.append("#include <ciso646>").append(NL);
		header.append("#include <forward_list>").append(NL);
		header.append("#include <iterator>").append(NL);
		header.append("#include <map>").append(NL);
		header.append("#include <vector>").append(NL);
		header.append("#include <set>").append(NL);
		header.append("#include <tuple>").append(NL);
		header.append("#include <type_traits>").append(NL);
		header.append("#include <unordered_map>").append(NL);
		header.append("#include <utility>").append(NL);
		header.append("#include <valarray>").append(NL);
		header.append(NL).append("#include \"BaseTypes.h\"").append(NL).append(NL);
		header.append(NL).append("//nlohmann/json").append(NL);
		header.append("#include <nlohmann/json.hpp>").append(NL);		
		header.append("using json = nlohmann::json;").append(NL).append(NL);
		
		for(String targetFileName : targetFileNameList) {
			header.append("#include \"").append(targetFileName).append("\"").append(NL);		
		}
		
		header.append(NL).append("using namespace std;").append(NL);
		header.append("using namespace ").append(NAMESPACE).append(";").append(NL);
		
		header.append(NL);
		testRes.insert(0, header.toString());
		
		File targetFile = new File(targetFilePath);
		if(targetFile.exists()) {
			targetFile.delete();
		}
		FileUtils.write(targetFile, testRes.toString(), StandardCharsets.UTF_8);
		
		header.setLength(0);
		testRes.setLength(0);
		return;
	}
	
	final void fillBaseType(StringBuilder res, String fieldType, String fieldName, String json) throws Exception {
		if("string".equals(fieldType)
				|| "double".equals(fieldType)
				|| "int".equals(fieldType)
				|| "short".equals(fieldType)
				|| "bool".equals(fieldType)
				|| "int32_t".equals(fieldType)
				|| "int64_t".equals(fieldType)) {
			res.append(spaceMap.get(indent)).append(fieldName).append(" = ").append(json).append("[\"").append(fieldName)
				.append("\"];").append(NL);
		}else if("unsigned char*".equals(fieldType) || "char*".equals(fieldType)) {
			res.append(spaceMap.get(indent)).append("tempbinstr = ").append(json).append("[\"").append(fieldName).append("\"];").append(NL);
			res.append(spaceMap.get(indent)).append("int tempLength = tempbinstr.length();").append(NL);
			res.append(spaceMap.get(indent)).append(fieldName).append(" = (").append(fieldType).append(")malloc(tempLength+1);").append(NL);
			res.append(spaceMap.get(indent)).append("if(!").append(fieldName).append(") {").append(NL);
			indent++;
			res.append(spaceMap.get(indent)).append("printf(\"内存不足! \\n\");").append(NL);
			indent--;
			res.append(spaceMap.get(indent)).append("} else {").append(NL);
			indent++;
			res.append(spaceMap.get(indent)).append("memcpy(").append(fieldName).append(", tempbinstr.c_str(), tempLength);").append(NL);
			res.append(spaceMap.get(indent)).append(fieldName).append("[tempLength]=0;").append(NL);
			indent--;
			res.append(spaceMap.get(indent)).append("}").append(NL);
		}else if("char".equals(fieldType)) {
			//char实际存储为int
			res.append(spaceMap.get(indent)).append(fieldName).append(" = ").append(json).append("[\"").append(fieldName)
			.append("\"].get<int>();").append(NL);
		}else {
			throw new Exception(fieldType + " is not a base type!");
		}
	}
	final String appValueOfBaseType(StringBuilder res, String fieldType, String varName, String srcName) throws Exception {
//		if("string".equals(fieldType)
//				|| "double".equals(fieldType)
//				|| "int".equals(fieldType)
//				|| "short".equals(fieldType)
//				|| "bool".equals(fieldType)
//				|| "int32_t".equals(fieldType)
//				|| "int64_t".equals(fieldType)) {
//			res.append(spaceMap.get(indent)).append(fieldType).append(" ").append(varName).append(" = ").append(srcName).append(";").append(NL);
//		}else 
			if("unsigned char*".equals(fieldType) || "char*".equals(fieldType)) {
			res.append(spaceMap.get(indent)).append("{").append(NL);
			indent++;
			
			res.append(spaceMap.get(indent)).append("string tempstr = ").append(srcName).append(";").append(NL);
			res.append(spaceMap.get(indent)).append("int tempLength = tempstr.length();").append(NL);
			res.append(spaceMap.get(indent)).append(varName).append(" = (").append(fieldType).append(")malloc(tempLength+1);").append(NL);
			res.append(spaceMap.get(indent)).append("if(!").append(varName).append(") {").append(NL);
			indent++;
			res.append(spaceMap.get(indent)).append("printf(\"内存不足! \\n\");").append(NL);
			indent--;
			res.append(spaceMap.get(indent)).append("} else {").append(NL);
			indent++;
			res.append(spaceMap.get(indent)).append("memcpy(").append(varName).append(", tempstr.c_str(), tempLength);").append(NL);
			res.append(spaceMap.get(indent)).append(varName).append("[tempLength]=0;").append(NL);
			indent--;
			res.append(spaceMap.get(indent)).append("}").append(NL);
			
			indent--;
			res.append(spaceMap.get(indent)).append("}").append(NL);
			return varName;
		}else if("char".equals(fieldType)) {
			//char实际存储为int
			res.append(spaceMap.get(indent)).append(fieldType).append(" ").append(varName).append(" = ").append(srcName).append(".get<int>();").append(NL);
			return varName;
		}else if("string".equals(fieldType)) {
			res.append(spaceMap.get(indent)).append(fieldType).append(" ").append(varName).append(" = ").append(srcName).append(";").append(NL);
			return varName;
		}
//		else {
//			throw new Exception(fieldType + " is not a base type!");
//		}
		return srcName;
	}
	final void fillBaseValue(StringBuilder res, String fieldType, String fieldName, String item) throws Exception {
		if("string".equals(fieldType)
				|| "double".equals(fieldType)
				|| "int".equals(fieldType)
				|| "short".equals(fieldType)
				|| "bool".equals(fieldType)
				|| "int32_t".equals(fieldType)
				|| "int64_t".equals(fieldType)) {
			res.append(fieldName).append(" = ").append(item).append("[\"").append(fieldName)
				.append("\"];").append(NL);
		}else if("unsigned char*".equals(fieldType)) {
			res.append("tempbinstr = ").append(item).append("[\"").append(fieldName)
			.append("\"];").append(NL)
			.append(fieldName).append(" = (unsigned char*)tempbinstr.c_str();").append(NL);
		}else if("char".equals(fieldType)) {
			//char实际存储为int
			res.append(fieldName).append(" = ").append(item).append("[\"").append(fieldName)
			.append("\"].get<int>();").append(NL);
		}else {
			throw new Exception(fieldType + " is not a base type!");
		}
	}
	final boolean isDigitTypeInCpp(String typeName) {
		switch(typeName) {
		case "double":
		case "int":
		case "int32_t":
		case "int64_t":
		case "short":
		return true;
		}
		return false;
	}
	final boolean isStringTypeInCpp(String typeName) {
		switch(typeName) {
		case "string":
		case "char*":
		case "char *":
		case "unsigned char*":
		case "const string":
		case "const char*":
		case "const char *":
		case "const unsigned char*":
			return true;
		}
		return false;
	}
	final boolean isCharStrTypeInCpp(String typeName) {
		switch(typeName) {
		case "char*":
		case "char *":
		case "unsigned char*":
		case "const char*":
		case "const char *":
		case "const unsigned char*":
			return true;
		}
		return false;
	}
	final void fillEnum(StringBuilder res, String fieldType, String fieldName, String json, boolean addBlock) {
		if(addBlock) {
			res.append(spaceMap.get(indent)).append("{").append(NL);
			indent++;
		}
		res.append(spaceMap.get(indent)).append("string enumKey = ").append(json).append("[\"").append(fieldName).append("\"];").append(NL);
		Map<String, Integer> enumMap = enumTypeMap.get(fieldType);
		boolean nonfirst = false;
		for(Entry<String, Integer> entry1 : enumMap.entrySet()) {
			if(nonfirst) {
				res.append(" else ");
			}else {
				res.append(spaceMap.get(indent));
				nonfirst = true;
			}
			res.append("if(strcmp(enumKey.c_str(), \"").append(entry1.getKey()).append("\") == 0){").append(NL);
			indent++;
			res.append(spaceMap.get(indent)).append(fieldName).append(" = ").append(entry1.getKey()).append(";").append(NL);
			decreaseIndent();
			res.append(spaceMap.get(indent)).append("}");
		}
		res.append(" else{").append(NL);
		indent++;
		res.append(spaceMap.get(indent)).append("printf(\"未知的枚举值: %s \\n\", enumKey.c_str());").append(NL);
		decreaseIndent();
		res.append(spaceMap.get(indent)).append("}").append(NL);
		if(addBlock) {
			decreaseIndent();
			res.append(spaceMap.get(indent)).append("}").append(NL);
		}
	}

	final void addBaseTypeToJson(StringBuilder res, String fieldType, String fieldName, String json) {
		if("string".equals(fieldType) || "const string".equals(fieldType)) {
			res.append(spaceMap.get(indent)).append("if(!").append(fieldName).append(".empty()) {").append(NL);
			indent++;
			res.append(spaceMap.get(indent)).append(json).append("[\"").append(fieldName).append("\"] = ").append(fieldName).append(";").append(NL);
			decreaseIndent();
			res.append(spaceMap.get(indent)).append("}").append(NL);
		}else if(this.isCharStrTypeInCpp(fieldType)){
			res.append(spaceMap.get(indent)).append("debug_printf(\"").append(fieldName).append("=%s \\n\", ").append(fieldName).append(");").append(NL); //testing
			res.append(spaceMap.get(indent)).append("if(").append(fieldName).append(" && strlen((const char*)").append(fieldName).append(") > 0) {").append(NL);
			indent++;
			res.append(spaceMap.get(indent)).append(json).append("[\"").append(fieldName).append("\"] = (const char*)").append(fieldName).append(";").append(NL);
			decreaseIndent();
			res.append(spaceMap.get(indent)).append("}").append(NL);
		}else {
			res.append(spaceMap.get(indent)).append(json).append("[\"").append(fieldName).append("\"] = ").append(fieldName).append(";").append(NL);
		}
	}
	
	//生成java代码
	public boolean genJava(String thriftFilePath, String thriftFileName, String targetDir) throws IOException {
		File srcfile = new File(thriftFilePath);
		if(!srcfile.exists()) {
			log("源文件不存在: " + thriftFilePath);
			return false;
		}
		String constantClassName = thriftFileName+ConstantSuffix;
		StringBuilder constantContent = new StringBuilder(); //常量
		List<String> localConstantVarList = new ArrayList<>();
		
		String generateTip = "@javax.annotation.Generated(value = \"Autogenerated by ClassUtil Compiler (0.1)\", date = \""+SDF5.format(new Date())+"\")";
		
		List<String> lines = FileUtils.readLines(srcfile, StandardCharsets.UTF_8);
		int lineCount = lines.size();
		List<String> enumTypeOrHeaders = new ArrayList<>();
		List<String> includeList = new ArrayList<>();
		Map<String, String> includeMap = new HashMap<>();
		Map<String, String> includeConstantMap = new HashMap<>();
		
		//获取package, include
		String namespace = null;
		for(int i=0;i<lineCount;i++) {
			String line = lines.get(i).trim();
			
			if(line.contains("namespace") && line.contains("java")) {
				namespace = line.substring(line.indexOf("java") + 4).trim();
				log("found namespace " + namespace);
			}else if(line.startsWith("include")) {
				String headerName = line.substring(line.indexOf('\"') + 1, line.indexOf('.')).toLowerCase();
				String includePkg = namespace.substring(0, namespace.lastIndexOf('.')) + "." + headerName;
				includeList.add("import "+includePkg+".*;");
				log("found include " + includePkg);
				String className = Character.toUpperCase(headerName.charAt(0))+headerName.substring(1);
				includeMap.put(className+".", className+".");
				includeConstantMap.put(className+".", className + ConstantSuffix + ".");
			}
		}
		includeList.add("import com.utm.util.IModel;");
		targetDir += "/" + namespace.replace('.', '/');
		File targetDirFile = new File(targetDir);
		if(!targetDirFile.exists()) {
			targetDirFile.mkdirs();
		}
		
		//常量提取
		for(int i=0;i<lineCount;i++) {
			String line = lines.get(i).trim();
			
			line = javaLineTranslate(line, includeMap, includeConstantMap, null, null);
			
			if(line.contains("namespace") && line.contains("java")) {
				namespace = line.substring(line.indexOf("java") + 4).trim();
				log("found namespace " + namespace);
			}else if(line.contains("const")) {
				line = line.replace("const", "public static final");
				String lastLine = lines.get(i-1).trim();
				if(lastLine.startsWith("//") || lastLine.startsWith("/*") || lastLine.startsWith("*/")) {
					//向上取注释
					List<String> templist = new ArrayList<>();
					boolean isBlockCmd = lastLine.startsWith("*/");
					for(int j = i-1;j>=0;j--) {
						lastLine = lines.get(j).trim();
						if(lastLine.startsWith("//") 
								|| lastLine.startsWith("/*") 
								|| lastLine.startsWith("*/")
								|| lastLine.startsWith("*")) {
							templist.add(0, lines.get(j));
							if(lastLine.startsWith("/*") && isBlockCmd) {
								break;
							}
						}else if(isBlockCmd){
							templist.add(0, lines.get(j));
						}else {
							break;
						}
					}
					for(String temp:templist) {
						constantContent.append(temp).append(NL);
					}
				}
				//处理常量中的map
				if(line.contains("Map<") && line.endsWith("{")) {
					line = line.split("=")[0].trim(); //去掉'{'
					//常量名称
					String varName;
					String[] arr = line.split(" ");
					varName = arr[arr.length - 1].trim();
					localConstantVarList.add(varName);
					
//					String leftType, rightType;
//					arr = line.substring(line.indexOf('<')+1, line.indexOf('>')).split(",");
//					leftType = arr[0].trim();
//					rightType = arr[1].trim();
					line += ";";
					constantContent.append(line).append(NL);
					constantContent.append("static { ").append(NL);
					constantContent.append(varName).append(" = new HashMap<>();").append(NL);
					String left, right;
					while(!line.startsWith("}")) {
						i++;
						line = lines.get(i).trim();
						if(line.startsWith("}"))
							break;
						arr = line.split(":");
						left = arr[0].trim();
						right = arr[1].trim();
						if(right.endsWith(",") || right.endsWith(";")) {
							right = right.substring(0, right.length() - 1);
						}
						constantContent.append(varName).append(".put(").append(left).append(",").append(right).append(");").append(NL);
					}
					if(line.endsWith(",")) 
						line = line.substring(0, line.length() - 1);
					if(!line.endsWith(";"))
						line += ";";
					constantContent.append(line).append(NL).append(NL);
				}else {
					if(line.endsWith(",")) 
						line = line.substring(0, line.length() - 1);
					if(!line.endsWith(";"))
						line += ";";
					constantContent.append(line).append(NL);
					
					//常量名称
					line = line.split("=")[0].trim(); //去掉'{'
					String varName;
					String[] arr = line.split(" ");
					varName = arr[arr.length - 1].trim();
					localConstantVarList.add(varName);
				}
			}
		}
		
		//生成常量文件
		String targetConstantFilePath = targetDir + "/" + constantClassName+".java";
		constantContent.append(NL);
		this.saveConstants(targetConstantFilePath, namespace, constantClassName, constantContent);
		
		//重新遍历, 每个类都生成一个文件
		
		for(int i=0;i<lineCount;i++) {
			String lineOri = lines.get(i);
			String line = lineOri.trim();

			lineOri = javaLineTranslate(lineOri, includeMap, includeConstantMap, constantClassName, localConstantVarList);

			//收集enum类型
			if(line.startsWith("enum")) {
				StringBuilder res = new StringBuilder(4096);
				String enumName = line.split("enum")[1].trim();
				if(enumName.endsWith("{")) {
					enumName = enumName.substring(0, enumName.length() - 1).trim();
				}
				enumTypeOrHeaders.add(enumName);
				log("found enum " + enumName);
				res.append("package ").append(namespace).append(";").append(NL).append(NL);
				res.append("import java.util.*;").append(NL).append(NL); 
				res.append("@SuppressWarnings({\"unused\"})").append(NL);
				res.append(generateTip).append(NL);
				res.append("public enum ").append(enumName).append(" { ").append(NL);
				List<String> enumFieldList = new ArrayList<>();
				List<Integer> enumFieldValueList = new ArrayList<>();
				int lastValue = 0;
				while(!"}".equals(line)) {
					i++;
					lineOri = lines.get(i);
					line = lineOri.trim();
					if("}".equals(line))
						break;
					
					lineOri = javaLineTranslate(lineOri, includeMap, includeConstantMap, constantClassName, localConstantVarList);

					if(line.startsWith("/*") 
							|| line.startsWith("//") 
							|| line.startsWith("*")
							|| line.length() == 0) {
						res.append(lineOri).append(NL);
						if(line.startsWith("/*")) {
							while(!line.contains("*/")) {
								i++;
								lineOri = lines.get(i);
								line = lineOri.trim();
								res.append(lineOri).append(NL);
							}
						}
					}else {
						if(line.contains("//")) {
							res.append(line.substring(line.indexOf("//")).trim());
							line = line.substring(0, line.indexOf("//")).trim();
						}
						if(line.contains("=")) {
							String[] arr = line.split("=");
							String left = arr[0].trim();
							String right = arr[1].trim();
							Integer value;
							char last = right.charAt(right.length() - 1);
							if(last == ',' || last == ';') {
								right = right.substring(0, right.length() - 1);
							}
							try {
								value = Integer.valueOf(right);
							}catch(Exception e) {
								throw e;
							}
							res.append(left+"("+right+"),").append(NL);
							enumFieldList.add(left);
							enumFieldValueList.add(value);
							lastValue = value;
						}else {
							if(line.endsWith(";"))
								line = line.substring(0, line.length() - 1).trim();
							if(line.endsWith(",")) {
								line = line.substring(0, line.length() - 1).trim();
							}
							enumFieldList.add(line);
							lastValue++;
							line += "("+lastValue+"),";
							res.append(line).append(NL);
							if(enumFieldValueList.size() == 0) {
								enumFieldValueList.add(0);
							}else {
								enumFieldValueList.add(enumFieldValueList.get(enumFieldValueList.size() - 1) + 1);
							}
						}
					}
				}
//				if("DroneCameraAction".equals(enumName)) {
//					log("stop");
//				}
				res.delete(res.length() - NL.length(), res.length());  //del NL
				res.deleteCharAt(res.length() - 1); //del ,
				res.append(';').append(NL).append(NL);
				
				res.append("private final int value;").append(NL).append(NL);
				res.append("private ").append(enumName).append("(int value) { this.value = value; }").append(NL).append(NL);
				res.append("public int getValue() { return value; }").append(NL).append(NL);
				//findByValue
				res.append("public static ").append(enumName).append(" findByValue(int value) {").append(NL)
					.append("switch (value) {").append(NL);
				for(int idx=0;idx<enumFieldList.size();idx++) {
					res.append("case ").append(enumFieldValueList.get(idx)).append(":").append(" return ").append(enumFieldList.get(idx)).append(";").append(NL);
				}
				res.append("default: return null;").append(NL).append("}").append(NL).append("}").append(NL);	
				//findByString
				res.append("public static ").append(enumName).append(" findByString(String value) {").append(NL)
					.append("switch (value) {").append(NL);
				for(int idx=0;idx<enumFieldList.size();idx++) {
					res.append("case \"").append(enumFieldList.get(idx)).append("\":").append(" return ").append(enumFieldList.get(idx)).append(";").append(NL);
				}
				res.append("default: return null;").append(NL).append("}").append(NL).append("}").append(NL);	
				
				res.append(NL).append("}").append(NL);
				
				String body = res.toString();
				
				String targetEnumFilePath = targetDir + "/" + enumName+".java";
				FileUtils.write(new File(targetEnumFilePath), body, StandardCharsets.UTF_8);
				res.setLength(0);
				res = null;
			}
			//解析类名
			if(line.startsWith("struct")) {
				StringBuilder res = new StringBuilder(4096);
				lineOri = lineOri.replace("struct", "public class")
						.replace("{", " implements IModel {");
				String className = line.split("struct")[1].trim();
				if(className.endsWith("{")) {
					className = className.substring(0, className.length() - 1).trim();
				}
				log("found class " + className);
				
				List<String> fieldTypeList = new ArrayList<>();
				List<String> fieldNameList = new ArrayList<>();
				
				res.append("package ").append(namespace).append(";").append(NL).append(NL);
				res.append("import java.util.*;").append(NL).append(NL); 
				res.append("import org.springframework.data.annotation.Id;").append(NL).append(NL); 
				for(String include : includeList) {
					res.append(include).append(NL);
				}
				int indexOfImportEnd = res.length();
				res.append(NL);
				res.append("@SuppressWarnings({\"unused\"})").append(NL);
				res.append(generateTip).append(NL);
				res.append(lineOri).append(NL);
				res.append("    ").append("private static final long serialVersionUID = 1L;").append(NL).append(NL);
				while(!"}".equals(line)) {
					i++;
					lineOri = lines.get(i);
					line = lineOri.trim();
					if("}".equals(line))
						break;
					if(line.startsWith("/*") 
							|| line.startsWith("//") 
							|| line.startsWith("*")
							|| line.length() == 0) {
						res.append(lineOri).append(NL);
						if(line.startsWith("/*")) {
							while(!line.contains("*/")) {
								i++;
								lineOri = lines.get(i);
								line = lineOri.trim();
								res.append(lineOri).append(NL);
							}
						}
					}else {						
						lineOri = javaLineTranslate(lineOri, includeMap, includeConstantMap, constantClassName, localConstantVarList);
						
						//解析成员变量
						if(line.contains("optional")) {
							line = lineOri.split("optional")[1].trim();
						}else if(line.contains("required")) {
							line = lineOri.split("required")[1].trim();
						}else if(line.contains(":")) {
							line = lineOri.split(":")[1].trim();
						}
						if(line.endsWith(",") || line.endsWith(";")) {
							line = line.substring(0, line.length() -1).trim();
						}
						String templine = line;
						if(templine.contains("=")) {
							templine = templine.split("=")[0].trim();
						}
						String[] arr = templine.split(" "); 
						fieldNameList.add(arr[arr.length - 1].trim());
						fieldTypeList.add(templine.substring(0, templine.length() - arr[arr.length - 1].length()).trim());
						
						line += ';';
						if(line.endsWith("String id;")) {
							res.append("    @Id").append(NL);
						}
						res.append("    ").append(javaFieldModifier).append(" ").append(line).append(NL);
					}
				}
				
				res.append(NL);
				
				//添加构造函数
				res.append("    ").append("public ").append(className).append("(){").append(NL);
					
				for(int idx=0;idx<fieldTypeList.size();idx++) {
					String fieldType = fieldTypeList.get(idx);
					String fieldName = fieldNameList.get(idx);
					if(javaDigitTypes.contains(fieldType)) {
						if("Float".equals(fieldType) || "float".equals(fieldType)) {
							res.append("    ").append("    ").append(fieldName).append(" = ").append("0F;").append(NL);
						}else if("Double".equals(fieldType) || "double".equals(fieldType)) {
							res.append("    ").append("    ").append(fieldName).append(" = ").append("0D;").append(NL);
						}else {
							res.append("    ").append("    ").append(fieldName).append(" = ").append("0;").append(NL);
						}
					}
				}
				res.append("    ").append("}").append(NL).append(NL);
				
				//添加get-set函数
				for(int idx=0;idx<fieldTypeList.size();idx++) {
					String fieldType = fieldTypeList.get(idx);
					String fieldName = fieldNameList.get(idx);
					String fieldNameM = Character.toUpperCase(fieldName.charAt(0))+fieldName.substring(1);
					res.append("    ").append("public final ").append(fieldType).append(" get").append(fieldNameM)
						.append("() { ").append(NL).append("    ").append("    ").append("return ").append(fieldName).append("; ").append(NL)
						.append("    ").append("}").append(NL).append(NL);
					res.append("    ").append("public final void set").append(fieldNameM).append("(").append(fieldType)
						.append(" ").append(fieldName).append(") { ").append(NL).append("    ").append("    ").append("this.")
						.append(fieldName).append(" = ").append(fieldName).append(";").append(NL)
						.append("    ").append("}").append(NL).append(NL);
				}
				
				res.append(NL).append("}").append(NL);
				
				if(res.indexOf(" State.") > 0) {
					res.insert(indexOfImportEnd, "import com.utm.model.status.State;" + NL);
				}
				
				String body = res.toString();
				
				String targetEnumFilePath = targetDir + "/" + className+".java";
				FileUtils.write(new File(targetEnumFilePath), body, StandardCharsets.UTF_8);
				res.setLength(0);
				res = null;
			}
		} //end for
		
		return true;
	}
	
	final boolean isPointer(String typeName) {
		if(typeName.startsWith("map") 
				|| typeName.startsWith("vector") 
				|| typeName.startsWith("set")
				|| classTypeList.contains(typeName)) {
			return true;
		}
		return false;
	}
	final String cppLinefilter(String lineOri) {
		String line = lineOri.replaceAll("i32", "int32_t")
				.replaceAll("i64", "int64_t")
				.replaceAll("i8", "char")
				.replaceAll("i16", "short")
				.replaceAll("list", "vector")
				.replaceAll("binary", "unsigned char*");
		return line;
	}
	
	final void decreaseIndent() {
		indent--;
		if(indent < 0)
			indent = 0;
	}
	final void addVersion(StringBuilder res) {
		res.append("/*******************************************************************************").append(NL);
		res.append(" * 版权所有 2017-2018 Shg.com").append(NL);
		res.append(" * 构建时间: ").append(SDF5.format(new Date())).append(NL);
		res.append(" * ").append(PRODUCT).append(NL);
		res.append(" * ").append(VERSION).append(NL);
		res.append(" *******************************************************************************/").append(NL);
		res.append(NL);
	}
	void saveConstants(String targetFileName, String pkg, String constantFileName, 
			StringBuilder constantContent) throws IOException{
		//
		String body = constantContent.toString();
		StringBuilder temp = new StringBuilder();
		temp.append("package ").append(pkg).append(";").append(NL).append(NL);
		if((body.contains("List<") || body.contains("Map<") || body.contains("Set<"))) {
			temp.append("import java.util.*;").append(NL).append(NL); 
		}
		String generateTip = "@javax.annotation.Generated(value = \"Autogenerated by ClassUtil Compiler (0.1)\", date = \""+SDF5.format(new Date())+"\")";
		temp.append(generateTip).append(NL);
		temp.append("public class ").append(constantFileName).append(" { ").append(NL)
			.append(body).append(NL).append(NL).append("} ").append(NL);
		FileUtils.write(new File(targetFileName), temp.toString(), StandardCharsets.UTF_8);
		return;
	}
	
	String javaLineTranslate(String line, Map<String,String> includeMap, Map<String,String> includeConstantMap, String constantClassName, List<String> localConstantVarList) {
		line = line.replaceAll("i32", "Integer")
				.replaceAll("i64", "Long")
				.replaceAll("double", "Double")
				.replaceAll("float", "Float")
				.replaceAll("list<", "List<")
				.replaceAll("map<", "Map<")
				.replaceAll("set<", "Set<")
				.replaceAll("binary", "byte[]")
				.replaceAll("string", "String")
				.replaceAll("bool", "Boolean");
		if(includeMap.size() > 0) {
			if(line.contains("=")) {
				String[] arr = line.split("=");
				for(Entry<String,String> entry : includeMap.entrySet()) {
					if(arr[0].contains(entry.getKey())) {
						arr[0] = arr[0].replace(entry.getKey(), "");
					}
				}
				for(Entry<String,String> entry : includeConstantMap.entrySet()) {
					if(arr[1].contains(entry.getKey())) {
						arr[1] = arr[1].replace(entry.getKey(), entry.getValue());
					}
				}
				String ori = line;
				line = arr[0].trim() +" = "+ arr[1].trim();
				log("替换 " + ori+" => " + line);
			}else {
				for(Entry<String,String> entry : includeMap.entrySet()) {
					if(line.contains(entry.getKey())) {
						String ori = line;
						line = line.replace(entry.getKey(), "");
						log("替换 " + ori+" => " + line);
					}
				}
			}
		}
		//本地常量
		if(line.contains("=") && localConstantVarList != null) {
			String[] arr = line.split("=");
			String value = arr[1].trim();
			if(value.endsWith(",") || value.endsWith(";")) {
				value = value.substring(0, value.length() - 1).trim();
			}
			for(String constantVar : localConstantVarList) {
				if(constantVar.equals(value)) {
					String ori = line;
					line = line.replace(constantVar, constantClassName+"." + constantVar);
					log("替换 " + ori+" => " + line);
				}
			}
		}
		//double
		if(line.contains("=") && line.contains(" Double ")) {
			String[] arr = line.split("=");
			String value = arr[1].trim();
			if(value.endsWith(",") || value.endsWith(";")) {
				value = value.substring(0, value.length() - 1).trim();
			}
			if(!value.contains(".")) {
				value += 'D';
				line = arr[0] + "= " + value + ";";
			}
		}
		return line;
	}
	final String generateValueOfType(String type) {
		if("string".equals(type)) {
			return "\"hello,world\"";
		}else if("double".equals(type)) {
			return "3.14";
		}else if("char".equals(type)) {
			return "'a'";
		}else {
			return "2018";
		}
	}
	final String convertToPtr(String typeName, String className) throws Exception {
		//目前只考虑2层,且假定key都是值类型
		String tempTypeName = typeName;
		if(tempTypeName.startsWith("map")) {
			String typeKey = tempTypeName.substring(tempTypeName.indexOf('<')+1, tempTypeName.indexOf(','));
			String typeValue = tempTypeName.substring(tempTypeName.indexOf(',')+1, tempTypeName.length() - 1).trim();
			if(typeValue.startsWith("map")) {
				String typeKey2 = typeValue.substring(typeValue.indexOf('<')+1, typeValue.indexOf(','));
				String typeValue2 = typeValue.substring(typeValue.indexOf(',')+1, typeValue.length() - 1).trim();
				if(isPointer(typeValue2)) {
					typeValue = "map<"+typeKey2+", "+typeValue2+"*>";
				}
			}else if(typeValue.startsWith("vector")) {
				String listValueType2 = typeValue.substring(typeValue.indexOf('<') + 1, typeValue.lastIndexOf('>'));
				if(isPointer(listValueType2)) {
					typeValue = "vector<" + listValueType2+"*>";
				}
			}else if(typeValue.startsWith("set")) {
				String setValueType2 = typeValue.substring(typeValue.indexOf('<') + 1, typeValue.lastIndexOf('>'));
				if(isPointer(setValueType2)) {
					typeValue = "set<" + setValueType2+"*>";
				}
			}
			if(isPointer(typeValue)) {
				tempTypeName = "map<" + typeKey+", " + typeValue + "*>";
			}
		}else if(tempTypeName.startsWith("vector")) {
			String listValueType = tempTypeName.substring(tempTypeName.indexOf('<') + 1, tempTypeName.lastIndexOf('>'));
			if(listValueType.startsWith("map")) {
				String typeKey2 = listValueType.substring(listValueType.indexOf('<')+1, listValueType.indexOf(','));
				String typeValue2 = listValueType.substring(listValueType.indexOf(',')+1, listValueType.length() - 1).trim();
				if(isPointer(typeValue2)) {
					listValueType = "map<"+typeKey2+", "+typeValue2+"*>";
				}
			}else if(listValueType.startsWith("vector")) {
				String listValueType2 = listValueType.substring(listValueType.indexOf('<') + 1, listValueType.lastIndexOf('>'));
				if(isPointer(listValueType2)) {
					listValueType = "vector<" + listValueType2+"*>";
				}
			}else if(listValueType.startsWith("set")) {
				String setValueType2 = listValueType.substring(listValueType.indexOf('<') + 1, listValueType.lastIndexOf('>'));
				if(isPointer(setValueType2)) {
					listValueType = "set<" + setValueType2+"*>";
				}
			}
			if(isPointer(listValueType)) {
				tempTypeName = "vector<" + listValueType+"*>";
			}
		}else if(tempTypeName.startsWith("set")) {
			String setValueType = tempTypeName.substring(tempTypeName.indexOf('<') + 1, tempTypeName.lastIndexOf('>'));
			if(setValueType.startsWith("map")) {
				String typeKey2 = setValueType.substring(setValueType.indexOf('<')+1, setValueType.indexOf(','));
				String typeValue2 = setValueType.substring(setValueType.indexOf(',')+1, setValueType.length() - 1).trim();
				if(isPointer(typeValue2)) {
					setValueType = "map<"+typeKey2+", "+typeValue2+"*>";
				}
			}else if(setValueType.startsWith("vector")) {
				String listValueType2 = setValueType.substring(setValueType.indexOf('<') + 1, setValueType.lastIndexOf('>'));
				if(isPointer(listValueType2)) {
					setValueType = "vector<" + listValueType2+"*>";
				}
			}else if(setValueType.startsWith("set")) {
				String setValueType2 = setValueType.substring(setValueType.indexOf('<') + 1, setValueType.lastIndexOf('>'));
				if(isPointer(setValueType2)) {
					setValueType = "set<" + setValueType2+"*>";
				}
			}
			if(isPointer(setValueType)) {
				tempTypeName = "set<" + setValueType+"*>";
			}
		}
		return tempTypeName;
	}
	final String convertToPtr2(String typeName, String className) throws Exception {
		//目前只考虑2层,且假定key都是值类型
		String tempTypeName = typeName;
		if(!this.isPointer(tempTypeName))
			return tempTypeName;
		if(tempTypeName.startsWith("map")) {
			String typeKey = tempTypeName.substring(tempTypeName.indexOf('<')+1, tempTypeName.indexOf(','));
			String typeValue = tempTypeName.substring(tempTypeName.indexOf(',')+1, tempTypeName.length() - 1).trim();
			if(typeValue.startsWith("map")) {
				String typeKey2 = typeValue.substring(typeValue.indexOf('<')+1, typeValue.indexOf(','));
				String typeValue2 = typeValue.substring(typeValue.indexOf(',')+1, typeValue.length() - 1).trim();
				if(isPointer(typeValue2)) {
					typeValue = "map<"+typeKey2+", "+typeValue2+"*>";
				}
			}else if(typeValue.startsWith("vector")) {
				String listValueType2 = typeValue.substring(typeValue.indexOf('<') + 1, typeValue.lastIndexOf('>'));
				if(isPointer(listValueType2)) {
					typeValue = "vector<" + listValueType2+"*>";
				}
			}else if(typeValue.startsWith("set")) {
				String setValueType2 = typeValue.substring(typeValue.indexOf('<') + 1, typeValue.lastIndexOf('>'));
				if(isPointer(setValueType2)) {
					typeValue = "set<" + setValueType2+"*>";
				}
			}
			if(isPointer(typeValue)) {
				tempTypeName = "map<" + typeKey+", " + typeValue + "*>";
			}
		}else if(tempTypeName.startsWith("vector")) {
			String listValueType = tempTypeName.substring(tempTypeName.indexOf('<') + 1, tempTypeName.lastIndexOf('>'));
			if(listValueType.startsWith("map")) {
				String typeKey2 = listValueType.substring(listValueType.indexOf('<')+1, listValueType.indexOf(','));
				String typeValue2 = listValueType.substring(listValueType.indexOf(',')+1, listValueType.length() - 1).trim();
				if(isPointer(typeValue2)) {
					listValueType = "map<"+typeKey2+", "+typeValue2+"*>";
				}
			}else if(listValueType.startsWith("vector")) {
				String listValueType2 = listValueType.substring(listValueType.indexOf('<') + 1, listValueType.lastIndexOf('>'));
				if(isPointer(listValueType2)) {
					listValueType = "vector<" + listValueType2+"*>";
				}
			}else if(listValueType.startsWith("set")) {
				String setValueType2 = listValueType.substring(listValueType.indexOf('<') + 1, listValueType.lastIndexOf('>'));
				if(isPointer(setValueType2)) {
					listValueType = "set<" + setValueType2+"*>";
				}
			}
			if(isPointer(listValueType)) {
				tempTypeName = "vector<" + listValueType+"*>";
			}
		}else if(tempTypeName.startsWith("set")) {
			String setValueType = tempTypeName.substring(tempTypeName.indexOf('<') + 1, tempTypeName.lastIndexOf('>'));
			if(setValueType.startsWith("map")) {
				String typeKey2 = setValueType.substring(setValueType.indexOf('<')+1, setValueType.indexOf(','));
				String typeValue2 = setValueType.substring(setValueType.indexOf(',')+1, setValueType.length() - 1).trim();
				if(isPointer(typeValue2)) {
					setValueType = "map<"+typeKey2+", "+typeValue2+"*>";
				}
			}else if(setValueType.startsWith("vector")) {
				String listValueType2 = setValueType.substring(setValueType.indexOf('<') + 1, setValueType.lastIndexOf('>'));
				if(isPointer(listValueType2)) {
					setValueType = "vector<" + listValueType2+"*>";
				}
			}else if(setValueType.startsWith("set")) {
				String setValueType2 = setValueType.substring(setValueType.indexOf('<') + 1, setValueType.lastIndexOf('>'));
				if(isPointer(setValueType2)) {
					setValueType = "set<" + setValueType2+"*>";
				}
			}
			if(isPointer(setValueType)) {
				tempTypeName = "set<" + setValueType+"*>";
			}
		}
		return tempTypeName + "*";
	}
}
