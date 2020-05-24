package io.cloudslang.lang.cli.utils;
import ch.lambdaj.function.convert.Converter;
import io.cloudslang.lang.api.Slang;
import io.cloudslang.lang.compiler.SlangSource;
import io.cloudslang.lang.compiler.SlangTextualKeys;
import io.cloudslang.lang.entities.CompilationArtifact;
import io.cloudslang.lang.entities.SystemProperty;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.apache.commons.lang.Validate;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import static ch.lambdaj.Lambda.convert;
/** 
 * @author lesant
 * @since 11/13/2014
 * @version $Id$
 */
@Component public class CompilerHelperImpl implements CompilerHelper {
  public static Logger logger=Logger.getLogger(CompilerHelperImpl.class);
  public String[] SLANG_FILE_EXTENSIONS={"sl","sl.yaml","sl.yml"};
  public static String[] YAML_FILE_EXTENSIONS={"yaml","yml"};
  public static String SP_DIR="properties";
  public static String INPUT_DIR="inputs";
  @Autowired public Slang slang;
  @Autowired public Yaml yaml;
  @Override public CompilationArtifact compile(  String filePath,  List<String> dependencies) throws IOException {
    Validate.notNull(filePath,"File path can not be null");
    Set<SlangSource> depsSources=new HashSet<>();
    File file=new File(filePath);
    Validate.isTrue(file.isFile(),"File: " + file.getName() + " was not found");
    boolean validFileExtension=checkIsFileSupported(file);
    Validate.isTrue(validFileExtension,"File: " + file.getName() + " must have one of the following extensions: sl, sl.yaml, sl.yml");
    if (CollectionUtils.isEmpty(dependencies)) {
      dependencies=new ArrayList<>();
      String appHome=System.getProperty("app.home","");
      String contentRoot=appHome + File.separator + "content";
      File contentRootDir=new File(contentRoot);
      if (StringUtils.isNotEmpty(appHome) && contentRootDir.exists() && contentRootDir.isDirectory()) {
        dependencies.add(contentRoot);
      }
 else {
        dependencies.add(file.getParent());
      }
    }
    for (    String dependency : dependencies) {
      Collection<File> dependenciesFiles=FileUtils.listFiles(new File(dependency),SLANG_FILE_EXTENSIONS,true);
      for (      File dependencyCandidate : dependenciesFiles) {
        SlangSource source=SlangSource.fromFile(dependencyCandidate);
        if (isExecutable(source)) {
          depsSources.add(source);
        }
 else {
          logger.info("Ignoring dependency: " + source.getName() + " (not a valid executable)."+ " Top level keys("+ SlangTextualKeys.FLOW_TYPE+ ", "+ SlangTextualKeys.OPERATION_TYPE+ ") not found.");
        }
      }
    }
    try {
      return slang.compile(SlangSource.fromFile(file),depsSources);
    }
 catch (    Exception e) {
      logger.error("Failed compilation for file : " + file.getName() + " ,Exception is : "+ e.getMessage());
      throw e;
    }
  }
  @Override public Set<SystemProperty> loadSystemProperties(  List<String> systemPropertyFiles){
    return loadPropertiesFromFiles(convertToFiles(systemPropertyFiles),SLANG_FILE_EXTENSIONS,SP_DIR);
  }
  @Override public Map<String,Serializable> loadInputsFromFile(  List<String> inputFiles){
    return loadMapsFromFiles(convertToFiles(inputFiles),YAML_FILE_EXTENSIONS,INPUT_DIR);
  }
  @Override public boolean isExecutable(  String filePath){
    return isExecutable(SlangSource.fromFile(new File(filePath)));
  }
  public Map<String,Serializable> loadMapsFromFiles(  List<File> files,  String[] extensions,  String directory){
    Collection<File> fileCollection;
    if (CollectionUtils.isEmpty(files)) {
      fileCollection=loadDefaultFiles(extensions,directory,false);
      if (CollectionUtils.isEmpty(fileCollection))       return null;
    }
 else {
      fileCollection=files;
    }
    Map<String,Serializable> result=new HashMap<>();
    for (    File inputFile : fileCollection) {
      logger.info("Loading file: " + inputFile);
      try {
        String inputsFileContent=FileUtils.readFileToString(inputFile);
        Boolean emptyContent=true;
        if (StringUtils.isNotEmpty(inputsFileContent)) {
          @SuppressWarnings("unchecked") Map<String,? extends Serializable> inputFileYamlContent=(Map<String,? extends Serializable>)yaml.load(inputsFileContent);
          if (MapUtils.isNotEmpty(inputFileYamlContent)) {
            emptyContent=false;
            result.putAll(inputFileYamlContent);
          }
        }
        if (emptyContent) {
          throw new RuntimeException("Inputs file: " + inputFile + " is empty or does not contain valid YAML content.");
        }
      }
 catch (      IOException ex) {
        logger.error("Error loading file: " + inputFile + ". Nested exception is: "+ ex.getMessage(),ex);
        throw new RuntimeException(ex);
      }
    }
    return result;
  }
  public Set<SystemProperty> loadPropertiesFromFiles(  List<File> files,  String[] extensions,  String directory){
    Collection<File> fileCollection;
    if (CollectionUtils.isEmpty(files)) {
      fileCollection=loadDefaultFiles(extensions,directory,true);
      if (CollectionUtils.isEmpty(fileCollection))       return new HashSet<>();
    }
 else {
      fileCollection=files;
    }
    Set<SystemProperty> result=new HashSet<>();
    for (    File propFile : fileCollection) {
      try {
        SlangSource source=SlangSource.fromFile(propFile);
        if (!isExecutable(source)) {
          logger.info("Loading file: " + propFile);
          Set<SystemProperty> propsFromFile=slang.loadSystemProperties(source);
          result.addAll(propsFromFile);
        }
 else {
          logger.info("Ignoring file: " + source.getName() + " (not a valid properties file)."+ " Top level keys("+ SlangTextualKeys.FLOW_TYPE+ ", "+ SlangTextualKeys.OPERATION_TYPE+ ") found.");
        }
      }
 catch (      Throwable ex) {
        String errorMessage="Error loading file: " + propFile + " nested exception is "+ ex.getMessage();
        logger.error(errorMessage,ex);
        throw new RuntimeException(errorMessage,ex);
      }
    }
    return result;
  }
  public Collection<File> loadDefaultFiles(  String[] extensions,  String directory,  boolean recursive){
    Collection<File> files;
    String appHome=System.getProperty("app.home","");
    String defaultDirectoryPath=appHome + File.separator + "bin"+ File.separator+ directory;
    File defaultDirectory=new File(defaultDirectoryPath);
    if (defaultDirectory.isDirectory()) {
      files=FileUtils.listFiles(defaultDirectory,extensions,recursive);
    }
 else {
      files=Collections.emptyList();
    }
    return files;
  }
  public Boolean checkIsFileSupported(  File file){
    String[] suffixes=new String[SLANG_FILE_EXTENSIONS.length];
    for (int i=0; i < suffixes.length; ++i) {
      suffixes[i]="." + SLANG_FILE_EXTENSIONS[i];
    }
    return new SuffixFileFilter(suffixes).accept(file);
  }
  public boolean isExecutable(  SlangSource source){
    Object yamlObject=yaml.load(source.getSource());
    if (yamlObject instanceof Map) {
      Map yamlObjectAsMap=(Map)yamlObject;
      return yamlObjectAsMap.containsKey(SlangTextualKeys.FLOW_TYPE) || yamlObjectAsMap.containsKey(SlangTextualKeys.OPERATION_TYPE);
    }
 else {
      return false;
    }
  }
  public List<File> convertToFiles(  List<String> fileList){
    return convert(fileList,new Converter<String,File>(){
      @Override public File convert(      String from){
        return new File(from);
      }
    }
);
  }
  public CompilerHelperImpl(){
  }
}
