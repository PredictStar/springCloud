package cn.nzxxx.predict.webrequest.controller;

import cn.nzxxx.predict.config.pdftable.FormPdf;
import cn.nzxxx.predict.config.pdftable.TablePdf;
import cn.nzxxx.predict.toolitem.entity.Help;
import cn.nzxxx.predict.toolitem.entity.ReturnClass;
import cn.nzxxx.predict.toolitem.service.TestPageServiceI;
import cn.nzxxx.predict.toolitem.tool.Helper;
import cn.nzxxx.predict.webrequest.service.PdfServiceI;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.pdmodel.PDDocument;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import technology.tabula.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.*;

@RestController
@RequestMapping("/pdf")
public class PDFController {
    private Logger logger = LoggerFactory.getLogger(this.getClass());
    @Autowired
    private PdfServiceI pdfSer;
	@Autowired
    private JdbcTemplate jdbcTemplate;
    /**
     *  http://localhost:8081/pdf/analysis?param=null
     *  urll 文件地址 "C:/Users/18722/Desktop/tolg/CRJ/section2.pdf"
     *  fileName 文件名 "section2.pdf"  MPD 维修计划文档
     * @return 状态说明
     * @throws Exception
     */
    @RequestMapping(value="/analysis")
    public String analysisInit(String param) throws Exception{
        String resstr;
        try{
            if(StringUtils.isBlank(param)){
                resstr=Help.returnClass(500,"参数异常","param值为空");
                return resstr;
            }
            Map map = Helper.stringJSONToMap(param);
            String urll=(String)map.get("urll");
            //测试
            //urll="C:/Users/18722/Desktop/tolg/CRJ/section9.pdf";
            String fileName=(String)map.get("fileName");
            //测试
            //fileName="section9.pdf";//"sloc.pdf";
            resstr=Help.return5002Describe(urll,fileName);
            if(resstr!=null){
                return resstr;
            }
            Date sdate=new Date();
            File file = new File(urll);
            InputStream input=new FileInputStream(file);
            //文件名要小写
            fileName=fileName.toLowerCase();
            String uuid = UUID.randomUUID().toString().replaceAll("-", "");
            TablePdf parPdf=new TablePdf();
            PDDocument document=parPdf.returnPDDocument(input);
            ObjectExtractor oe  = new ObjectExtractor(document);
            //页面总数
            //每一页单独处理
            int pagenum=parPdf.retPagenum(document);
            int cou=0;//sql执行条数
            //有效页面记录-测试用
            //String yxym="";
            for(int i=1;i<=pagenum;i++){
                Map conditionsMap=new HashMap();
                List<List<String>> newrows=new ArrayList<List<String>>();
                //如果是 sloc.pdf 文件 通过原生表线方式去获取
                if(fileName.equals("sloc.pdf")){
                    newrows= parPdf.reTaData(oe,i,conditionsMap,fileName);
                    if(newrows.size()==0){
                        continue;
                    }
                }else{
                    conditionsMap=parPdf.retCondMap(oe,"x",i,fileName);//页数从1开始
                    if(conditionsMap.size()==0){
                        continue;
                    }
                    newrows= parPdf.parsePdf(conditionsMap);
                }
                Map<String,Object> paramMap=new HashMap<String,Object>();
                paramMap.put("uuid",uuid);
                paramMap.put("fileName",fileName);
                String sql=parPdf.retInSql(newrows,conditionsMap,paramMap);
                if(StringUtils.isBlank(sql)){
                    continue;
                }
                //yxym+=i+";";
                //页面会有未完待续,然后下个页面继续录入的情况,所以提取数据时要注意此情况
                int update = jdbcTemplate.update(sql);
                cou+=update;
            }
            //单独测试某页(测试时一般开启 "数据输出" )
            /*int testpage=14;//从1开始
            Map conditionsMap=new HashMap();
            List<List<String>> newrows=new ArrayList<List<String>>();
            //如果是 sloc.pdf 文件 通过原生表线方式去获取
            if(fileName.equals("sloc.pdf")){
                newrows= parPdf.reTaData(oe,testpage,conditionsMap,fileName);
                if(newrows.size()==0){
                    return "[\"页面未匹配\"]";
                }
            }else{
                conditionsMap=parPdf.retCondMap(oe,"x",testpage,fileName);//页数从1开始
                if(conditionsMap.size()==0){
                    return "[\"拦截页面了\"]";
                }
                newrows= parPdf.parsePdf(conditionsMap);
            }
            Map<String,Object> paramMap=new HashMap<String,Object>();
            paramMap.put("uuid",uuid);
            paramMap.put("fileName",fileName);
            String sql=parPdf.retInSql(newrows,conditionsMap,paramMap);
            System.out.println(sql);
            int update = jdbcTemplate.update(sql);
            cou+=update;*/

            Date edate=new Date();
            String timedes="执行完成;执行时间;"+(edate.getTime()-sdate.getTime())/1000+"s";
            String couS=";插入"+cou+"条;同一批次号:"+uuid;
            resstr=Help.returnClass(200,timedes+couS,uuid);
            //关
            parPdf.closed(oe,document,input);

        }catch (Exception e){
            String strE=Helper.exceptionToString(e);
            logger.error(strE);
            String strEInfo=strE.substring(0,500>strE.length()?strE.length():500);
            System.out.println(strEInfo);
            resstr=Help.returnClass(500,"接口异常",strEInfo);
        }
        return resstr;

    }
    /**
     * http://localhost:8181/pdf/executePDFForm?AMMID=2
     * @return 状态说明
     * @throws Exception
     * 使用: 从 amm_file 提取标记是是0的,进行解析PDF是Form格式的(即工卡)
     */
    @RequestMapping("/executePDFForm")
    public ReturnClass executePDFForm(String AMMID){
        ReturnClass reC= Help.return5001DescribeT(AMMID);
        if(reC!=null){
            return reC;
        }
        reC=Help.returnClassT(200,"executePDFForm操作成功","");
        Date sdate=new Date();
        List<Map<String, Object>> pdf = getPDF(AMMID);
        if(pdf.size()==0){
            reC=Help.returnClassT(200,"文件表无查询结果","");
        }else{
            for(int i=0;i<pdf.size();i++){
                Map<String, Object> object = pdf.get(i);
                ReturnClass ReC = analysisPDFForm(object);
                //System.out.println(ReC);
                if(!ReC.getStatusCode().equals("200")){
                    //报错回滚数据:使 IS_EXECUTE=0
                    update( pdf,i,0);
                    return ReC;
                }
            }
        }
        Date edate=new Date();
        String timedes="执行时间;"+(edate.getTime()-sdate.getTime())/1000+"s";
        reC.setValueDescribe(timedes);
        return reC;
    }
    //查询需操作文件
    public List<Map<String, Object>> getPDF(String AMMID){
        String sql="SELECT\n" +
                "f.AMM_FILE_ID,\n" +
                "f.FILENAME,\n" +
                "f.FILETYPE,\n" +
                "f.AMMPATH,\n" +
                "f.AMMID\n"+
                "FROM\n" +
                "amm_file AS f\n" +
                "WHERE\n" +
                "f.IS_EXECUTE = 0\n" +
                "and f.AMMID="+AMMID;
        List<Map<String, Object>> re=jdbcTemplate.queryForList(sql);
        if(re.size()>0){
            //占数据,使 IS_EXECUTE=1
            int updateN=update( re,0,1);
            if(updateN!=re.size()){
                String s="getPDF方法数据争取存在!!!";
                logger.error(s);
                System.out.println(s);
            }
        }
        return re;
    }
    public int update(List<Map<String, Object>> re,int init,int IS_EXECUTE){
        String strin="";
        for(int i=init;i<re.size();i++){
            Map<String, Object> object = re.get(i);
            Integer key=(Integer) object.get("AMM_FILE_ID");
            if(key==null){
                key=0;
            }
            if(i==0){
                strin=String.valueOf(key);
            }else {
                strin+=","+String.valueOf(key);
            }
        }
        String updatesql="update amm_file set IS_EXECUTE="+IS_EXECUTE+" where AMM_FILE_ID in ("+strin+") and IS_EXECUTE = 0";
        int update = jdbcTemplate.update(updatesql);
        return update;
    }
    public ReturnClass analysisPDFForm(Map pdfMap){
        ReturnClass reC=Help.returnClassT(200,"analysisPDFForm操作成功","");
        String urll=(String)pdfMap.get("AMMPATH");
        //文件存储的上级文件夹名,这样就能通过文件夹指定工卡通过此pdf生成的,存的文件名是工卡表主键(如 CRJ_CARD BOEING_CARD)
        String folderName=(String)pdfMap.get("FILENAME");
        Integer AMMID=(Integer)pdfMap.get("AMMID");
        if(AMMID==null){
            AMMID=0;
        }
        folderName=AMMID+"/"+folderName;
        String fileType=(String)pdfMap.get("FILETYPE");
        Integer AMM_FILE_ID=(Integer)pdfMap.get("AMM_FILE_ID");
        ReturnClass reP=Help.return5003DescribeT(urll,folderName,fileType);
        if(reP!=null){
            return reP;
        }
        try{
            Date sdate=new Date();
            File file = new File(urll);
            InputStream input=new FileInputStream(file);
            //初始化FormPdf类
            FormPdf fpdf=new FormPdf();
            fpdf.setFileType(fileType);
            PDDocument document=fpdf.returnPDDocument(input);
            ObjectExtractor oe  = new ObjectExtractor(document);
            //页面总数(从1开始)
            int pagenum=fpdf.retPagenum(document);
            //是否是一条完整的解析(一个word可能由多个pdf页构成)
            int num=0;
            //解析pdf,后赋word所需内容
            Map<String,Object> analyPdfM=new HashMap<String,Object>();
            //提取值规则定义
            List<Map<String,Object>> ruleList=fpdf.getNewRule();
            //循环所有pdf页 -暂时先循环一次
            //for(int i=123;i<=pagenum;i++){ //测试-后期去掉
            for(int i=1;i<=pagenum;i++){
                Page page=fpdf.retPageC(oe,i);
                //测试-后期去掉
                /*if(i==128){
                    i=pagenum;
                }*/
                //当前页的类型(1:word的首页;2:需解析的页面;)
                int pageTypeN = fpdf.pageType(page);
                if(pageTypeN==0&&analyPdfM.size()==0){ //去掉无用的页面(在数据后的0是图)
                    continue;
                }
                if(pageTypeN==1){
                    //之前解析好的数据,生成word,入数据库
                    if(analyPdfM.size()!=0){
                        //生成word,入数据库
                        reC=fpdf.run(folderName,analyPdfM,AMM_FILE_ID,jdbcTemplate);
                        //清空analyPdfM
                        analyPdfM=new HashMap<String,Object>();
                        ruleList=fpdf.getNewRule();
                        num++;
                    }
                }
                //解析PDF
                fpdf.analyPdfToMap(page,document,i,analyPdfM,pageTypeN,ruleList);
                if(i==pagenum){ //最后一页
                    if(analyPdfM.size()!=0){
                        //生成word,入数据库
                        reC=fpdf.run(folderName,analyPdfM,AMM_FILE_ID,jdbcTemplate);
                        //清空analyPdfM
                        analyPdfM=new HashMap<String,Object>();
                        ruleList=fpdf.getNewRule();
                        num++;
                    }
                }
                if(!reC.getStatusCode().equals("200")){
                    return reC;
                }
            }
            //关
            fpdf.closed(oe,document,input);
            Date edate=new Date();
            String timedes=";执行时间;"+(edate.getTime()-sdate.getTime())/1000+"s";
            return Help.returnClassT(200,"解析"+folderName+"成功","生成个数:"+num+timedes);
        }catch(Exception e){
            String strE=Helper.exceptionToString(e);
            logger.error(strE);
            String strEInfo=strE.substring(0,500>strE.length()?strE.length():500);
            System.out.println(strEInfo);
            reC=Help.returnClassT(500,"解析"+folderName+"异常",strEInfo);
            return reC;
        }
    }
    /**
     *  下载翻译后的工卡word
     *  http://localhost:8081/pdf/translateTaskCard?idd=760&type=crj
     *  idd 是 crj_card|boeing 表的主键
     *  type 是 crj|boeing|amms
     * @throws Exception
     */
    @RequestMapping(value="/translateTaskCard")
    public String translateTaskCard(String idd,String type, HttpServletRequest request, HttpServletResponse response){
        String re="";
        try{
            String analyPdfData=pdfSer.getAnalyPdfData(idd,type);
            if(StringUtils.isBlank(analyPdfData)){
                return re;
            }
            //解决 stringJSONToMap 会报错的问题(analyPdfData的colMatch(列匹配规则中有\导致))
            String analyPdfDataN=analyPdfData.replaceAll("\\\\","反斜杠暂时去掉");
            //System.out.println(analyPdfDataN);
            Map analyPdfM = Helper.stringJSONToMap(analyPdfDataN);

            //配置文件值获取
            ResourceBundle resourceBundle = java.util.ResourceBundle.getBundle("amms");//amms.properties里值
            String typepath = java.util.ResourceBundle.getBundle("application").getString("spring.profiles.active");//application.properties里值
            String saveMain=java.util.ResourceBundle.getBundle("application-"+typepath).getString("saveurl.main");
            String saveExtend = resourceBundle.getString("saveurl.taskcard.extend");
            //保存后的文件夹位置(要事先存在)
            String saveUrl=saveMain+saveExtend+type+"Trans";
            // 创建文件夹
            File file = new File(saveUrl);
            if (!file.exists()) {
                file.mkdirs();
            }
            //翻译word所存储位置
            analyPdfM.put("saveUrl",saveUrl);
            analyPdfM.put("saveName",idd);
            re=pdfSer.translateTaskCard(analyPdfM,request,response);
        }catch (Exception e){
            String strE=Helper.exceptionToString(e);
            logger.error(strE);
            String strEInfo=strE.substring(0,500>strE.length()?strE.length():500);
            System.out.println(strEInfo);
        }
        return re;
    }

    /**
     * CRJ BOEING 数据翻译并入 job_card_body 库
     * @param idd
     * @param type
     * jobCardId   JobCard 表主键
     * @return
     */
    @RequestMapping(value="/transTCInStorage")
    public String transTCInStorage(String idd,String type,Integer jobCardId){
        String re="";
        try{
            String analyPdfData=pdfSer.getAnalyPdfData(idd,type);
            if(StringUtils.isBlank(analyPdfData)){
                return re;
            }
            //解决 stringJSONToMap 会报错的问题(analyPdfData的colMatch(列匹配规则中有\导致))
            String analyPdfDataN=analyPdfData.replaceAll("\\\\","反斜杠暂时去掉");
            Map analyPdfM = Helper.stringJSONToMap(analyPdfDataN);
            re=pdfSer.transTCInStorage(analyPdfM,jobCardId);
        }catch (Exception e){
            String strE=Helper.exceptionToString(e);
            logger.error(strE);
            String strEInfo=strE.substring(0,500>strE.length()?strE.length():500);
            System.out.println(strEInfo);
        }
        return re;
    }
}
