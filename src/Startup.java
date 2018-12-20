import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FilenameUtils;

import com.alibaba.fastjson.JSON;

public class Startup {

	
	
	public static void main(String[] args) throws Exception {
	
		String baseSrcDir = "E:\\JavaProjects\\fcow\\utm-model\\thrift\\";
		List<String> filePathList = new ArrayList<>();
		filePathList.add(baseSrcDir + "General.thrift");
		filePathList.add(baseSrcDir + "User.thrift");
		filePathList.add(baseSrcDir + "Device.thrift");
		filePathList.add(baseSrcDir + "Route.thrift");
		filePathList.add(baseSrcDir + "Para.thrift");
		filePathList.add(baseSrcDir + "Drone.thrift");

		genCpp(filePathList);
		
		genJava(filePathList);
		
		ThriftParser.log("over");
	}
	
	static void genCpp(List<String> filePathList) throws Exception {
		String root = ThriftParser.NAMESPACE + "/cpp";
		File dir = new File(root);
		if(!dir.exists()) {
			dir.mkdirs();
		}
		ThriftParser parser = new ThriftParser();
		ThriftParser.log("begin parsing cpp");
		for(String filePath : filePathList) {
			ThriftParser.log("parsing " + filePath);
			String fileName = FilenameUtils.getName(filePath);
			fileName = root + "/" + fileName.replace(".thrift", ".h");
			parser.genCpp(filePath, fileName);
			ThriftParser.log("parsing " + fileName + " over");
		}
		ThriftParser.log("end parsing cpp");
		
		ThriftParser.log("begin write test file");
		String fileName = root + "/test.cpp";
		parser.addTestEntranceAndWriteFile(fileName);
		ThriftParser.log("end write test file");
	}
	
	static void genJava(List<String> filePathList) throws Exception {
		String root = ThriftParser.NAMESPACE + "/java";
		File dirFile = new File(root);
		if(!dirFile.exists()) {
			dirFile.mkdirs();
		}
		ThriftParser parser = new ThriftParser();
		ThriftParser.log("begin parsing java");
		for(String filePath : filePathList) {
			ThriftParser.log("parsing " + filePath);
			String fileName = FilenameUtils.getName(filePath).replace(".thrift", "");
			parser.genJava(filePath, fileName, root);
			ThriftParser.log("parsing " + fileName + " over");
		}
		ThriftParser.log("end parsing java");
	}


}
