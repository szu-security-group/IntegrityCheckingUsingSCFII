package com.fchen_group.TPDSInScf.Run;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.fchen_group.TPDSInScf.Core.ChallengeData;
import com.fchen_group.TPDSInScf.Core.IntegrityAuditing;
import com.fchen_group.TPDSInScf.Core.ProofData;
import com.fchen_group.TPDSInScf.Utils.CloudAPI;
import com.fchen_group.TPDSInScf.Utils.ResponseClass;
import com.fchen_group.TPDSInScf.Utils.TenRequestClass;
import com.fchen_group.TPDSInScf.Utils.TenYunControl;
import sun.net.www.protocol.https.Handler;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;

/**
 * This class define the action of the client in the audit process
 *
 * @author jquan, fchen-group of SZU
 * @Version 2.0 2021.12.02
 * @Time 2021.3 - 2021.12
 */

public class Client {
    /**
     * this method used to check the correctness of the protocol
     */
    //delete
    public static void main(String args[]) throws IOException {
        auditTask(255, 223);
    }
    static long time[] = new long[5];
    /**
     * the action of the client in the audit process
     * @param BLOCK_SHARDS PROTOCOL PARAMETER
     * @param DATA_SHARDS  PROTOCOL PARAMETER
     */
    public static void auditTask(int BLOCK_SHARDS, int DATA_SHARDS) throws IOException {
        String cosConfigFilePath = System.getProperty("user.dir")+"\\Properties";
        Properties properties = new Properties();
        String filePath=null;
        String tmpFilePath=null;
        int partNum = 0;
        int bolckNum =0;
        try {
            FileInputStream fis = new FileInputStream(cosConfigFilePath);
            properties.load(fis);
            fis.close();

            // 尝试读取 filePath 和 tmpFilePath
            filePath = properties.getProperty("filePath");
            tmpFilePath = properties.getProperty("tmpFilePath");
            partNum = Integer.parseInt(properties.getProperty("partNum"));
            bolckNum = Integer.parseInt(properties.getProperty("blockNum"));

            // 检查是否读取到了值，如果没有则抛出异常
            if (filePath == null || filePath.isEmpty()) {
                throw new IllegalArgumentException("配置文件中缺少 filePath。");

            }
            if (tmpFilePath == null || tmpFilePath.isEmpty()) {
                throw new IllegalArgumentException("配置文件中缺少 tmpFilePath。");
            }
            if (partNum <= 0) {
                throw new IllegalArgumentException("配置文件中 partNum 数目为非正数。");
            }
            if (bolckNum <= 0) {
                throw new IllegalArgumentException("配置文件中 bolckNum 数目为非正数。");
            }

            // 打印输出验证读取是否成功
            System.out.println("File Path: " + filePath);
            System.out.println("Temporary File Path: " + tmpFilePath);

        } catch (IOException e) {
            System.out.println("配置文件读取失败：" + e.getMessage());
        } catch (IllegalArgumentException e) {
            System.out.println("错误：" + e.getMessage());
        }

        String uploadSourceFilePath = tmpFilePath+"\\sourceFile.txt";
        String uploadParitiesPath = tmpFilePath+"\\parities.txt";
        String proofDataStoragePath = tmpFilePath+"\\proofData.txt";
        System.out.println(uploadParitiesPath);

        IntegrityAuditing integrityAuditing = new IntegrityAuditing(filePath, BLOCK_SHARDS, DATA_SHARDS);
        //0-KeyGen , 1-DataProcess , 2-OutSource , 3-Audit , 4-Verify ,x-Prove(从腾讯云控制台读取)
        long time[] = new long[5];


        //start auditing
        System.out.println("---KeyGen phase start---");
        long start_time_genKey = System.nanoTime();
        integrityAuditing.genKey();
        long end_time_genKey = System.nanoTime();
        time[0] = end_time_genKey - start_time_genKey;
        System.out.println("---KeyGen phase finished---");

        int lun = integrityAuditing.SHARD_NUMBER / integrityAuditing.my_shard;
        int last = integrityAuditing.SHARD_NUMBER % integrityAuditing.my_shard;
        FileInputStream in = new FileInputStream(filePath);


        File uploadSourceFile = new File(uploadSourceFilePath);
        uploadSourceFile.createNewFile();
        OutputStream osFile = new FileOutputStream(uploadSourceFile, false);

        File uploadParities = new File(uploadParitiesPath);
        uploadParities.createNewFile();
        OutputStream osParities = new FileOutputStream(uploadParities, false);

        System.out.println(lun);
        System.out.println(last);
        long start_time_outsource = System.nanoTime();

//        这个地方在循环外包
        for (int i = 0; i <= lun; i++) {
            if (i == lun) {
                for (int j = 0; j <last; j++) {
                    in.read(integrityAuditing.originalData[j]);
                }
                for (int j = 0; j < last; j++) {
                    osFile.write(integrityAuditing.originalData[j]);
                }
                integrityAuditing.outSource(last);
                for (int j = 0; j < last; j++) {
                    osParities.write(integrityAuditing.parity[j]);
                }
                break;
            }
            for (int j = 0; j <integrityAuditing.my_shard; j++) {
                in.read(integrityAuditing.originalData[j]);
            }
            for (int j = 0; j < integrityAuditing.my_shard; j++) {
                osFile.write(integrityAuditing.originalData[j]);
            }
            integrityAuditing.outSource(integrityAuditing.my_shard);
            for (int j = 0; j < integrityAuditing.my_shard; j++) {
                osParities.write(integrityAuditing.parity[j]);
            }
        }
        in.close();
        osFile.close();
        osParities.close();

        long end_time_outSource=System.nanoTime();
        long sourceFileSize = uploadSourceFile.length();
        long extraStorageSize = uploadParities.length();
        time[1] =end_time_outSource -start_time_outsource;

        //upload File and tags to COS
        long start_time_upload = System.nanoTime();
        CloudAPI cloudAPI = new CloudAPI(cosConfigFilePath);
        //cloudAPI.uploadFile(uploadParitiesPath,"parities.txt");
        cloudAPI.multipartUpload(uploadSourceFilePath,bolckNum,"sourceFile.txt");
        cloudAPI = new CloudAPI(cosConfigFilePath);
        cloudAPI.multipartUpload(uploadParitiesPath,1, "parities.txt");

        System.out.println("upload File and tags to COS");
        long end_time_upload = System.nanoTime();
        time[2] = end_time_upload - start_time_upload;
        System.out.println("---OutSource phase finished---");

        //prepare challengeData
        System.out.println("---Audit phase start---");
        ChallengeData challengeData = integrityAuditing.audit(460);


        FileInputStream propertiesFIS = new FileInputStream(cosConfigFilePath);
        properties.load(propertiesFIS);
        propertiesFIS.close();
        String secretId = properties.getProperty("secretId");
        String secretKey = properties.getProperty("secretKey");
        String region = properties.getProperty("regionName");
        String bucketName = properties.getProperty("bucketName");
//
        long start_time_audit = System.nanoTime();
        TenYunControl tenYunControl=new TenYunControl();
        TenRequestClass tenRequestClass=new TenRequestClass(BLOCK_SHARDS - DATA_SHARDS,DATA_SHARDS,challengeData,bucketName,region,secretId,secretKey,partNum);
        String input= JSON.toJSONString(tenRequestClass);
        String result=tenYunControl.invoke(input);
        long end_time_audit = System.nanoTime();
        time[3] = end_time_audit - start_time_audit;
        System.out.println("返回结果为"+result);
        ResponseClass responseClass=JSON.parseObject(result,ResponseClass.class);

//
        ProofData proofData = responseClass.proofData;
        long proofTime =responseClass.proofTime;
        long download_time=responseClass.download_time;
        System.out.println("proofTime"+proofTime);
        System.out.println("download_time"+download_time);
//
        // System.out.println("Get proofData content:" + proofData.dataProof.toString() + "\t and" + proofData.parityProof.toString());
        //write proofData to file，calculate the communication cost

        File proofDataCost = new File(proofDataStoragePath);
        proofDataCost.createNewFile();
        OutputStream osProofData = new FileOutputStream(proofDataCost, false);
        osProofData.write(proofData.parityProof);
        osProofData.write(proofData.dataProof);
        osProofData.close();

        //cal communication cost
        long proofDataSize = proofDataCost.length();
        System.out.println("proofDataSize is " + proofDataSize + " Bytes");


        //execute verify parse
        System.out.println("---Verify phase start---");
        long start_time_verify = System.nanoTime();
        System.out.println(JSON.toJSONString(proofData));
        if (integrityAuditing.verify(challengeData, proofData)) {
            System.out.println("---Verify phase finished---");
            System.out.println("The data is intact in the cloud.The auditing process is success!");
        }
        else {
            System.out.println("The data was changed");
        }
        long end_time_verify = System.nanoTime();
        time[4] = end_time_verify - start_time_verify;

//
//        //store the performance in local
//        String performanceFilePath = new String("E:\\project\\file\\result.txt");
//        File performanceFile = new File(performanceFilePath);
//
//        if (performanceFile.exists() && taskCount == 1) {
//            performanceFile.delete();
//        }
//        performanceFile.createNewFile();
//        FileWriter resWriter = new FileWriter(performanceFile, true);
//
//        String title = "Audit data size is " + String.valueOf(sourceFileSize) + ". No." + String.valueOf(taskCount) + " audit process. \r\n";
//        resWriter.write(title);
//
//        resWriter.write("StorageCost " + String.valueOf(extraStorageSize) + "  CommunicationCost " + String.valueOf(proofDataSize) + "\r\n");
//        for (int i = 0; i < 5; i++) {
//            resWriter.write("time[" + i + "] = " + String.valueOf(time[i]) + "  ");
//        }
//        resWriter.write("\r\n");
//        resWriter.close();
    }
    public static void outSource(String filePath, int BLOCK_SHARDS, int DATA_SHARDS) throws IOException {

        IntegrityAuditing integrityAuditing = new IntegrityAuditing(filePath, BLOCK_SHARDS, DATA_SHARDS);
        String cosConfigFilePath = System.getProperty("user.dir")+"\\Properties";
        //0-KeyGen , 1-DataProcess , 2-OutSource , 3-Audit , 4-Verify ,x-Prove(从腾讯云控制台读取)


        //start auditing
        System.out.println("---KeyGen phase start---");
        long start_time_genKey = System.nanoTime();
        integrityAuditing.genKey();
        long end_time_genKey = System.nanoTime();
        time[0] = end_time_genKey - start_time_genKey;
        System.out.println("---KeyGen phase finished---");

        int lun = integrityAuditing.SHARD_NUMBER / integrityAuditing.my_shard;
        int last = integrityAuditing.SHARD_NUMBER % integrityAuditing.my_shard;
        FileInputStream in = new FileInputStream(filePath);
        String uploadSourceFilePath = "E:\\project\\file\\sourceFile.txt";
        String uploadParitiesPath = "E:\\project\\file\\parities.txt";

        File uploadSourceFile = new File(uploadSourceFilePath);
        uploadSourceFile.createNewFile();
        OutputStream osFile = new FileOutputStream(uploadSourceFile, false);

        File uploadParities = new File(uploadParitiesPath);
        uploadParities.createNewFile();
        OutputStream osParities = new FileOutputStream(uploadParities, false);

        System.out.println(lun);
        System.out.println(last);
        long start_time_outsource = System.nanoTime();

//        这个地方在循环外包
        for (int i = 0; i <= lun; i++) {
            if (i == lun) {
                for (int j = 0; j <last; j++) {
                    in.read(integrityAuditing.originalData[j]);
                }
                for (int j = 0; j < last; j++) {
                    osFile.write(integrityAuditing.originalData[j]);
                }
                integrityAuditing.outSource(last);
                for (int j = 0; j < last; j++) {
                    osParities.write(integrityAuditing.parity[j]);
                }
                break;
            }
            for (int j = 0; j <integrityAuditing.my_shard; j++) {
                in.read(integrityAuditing.originalData[j]);
            }
            for (int j = 0; j < integrityAuditing.my_shard; j++) {
                osFile.write(integrityAuditing.originalData[j]);
            }
            integrityAuditing.outSource(integrityAuditing.my_shard);
            for (int j = 0; j < integrityAuditing.my_shard; j++) {
                osParities.write(integrityAuditing.parity[j]);
            }
        }
        in.close();
        osFile.close();
        osParities.close();

        long end_time_outSource=System.nanoTime();
        time[1] =end_time_outSource -start_time_outsource;

        //upload File and tags to COS
        long start_time_upload = System.nanoTime();
        CloudAPI cloudAPI = new CloudAPI(cosConfigFilePath);

        cloudAPI.uploadFile(uploadSourceFilePath, "sourceFile.txt");
        cloudAPI.uploadFile(uploadParitiesPath, "parities.txt");

        System.out.println("upload File and tags to COS");
        long end_time_upload = System.nanoTime();
        time[2] = end_time_upload - start_time_upload;
        System.out.println("---OutSource phase finished---");

    }
    public static void audit(String filePath, int BLOCK_SHARDS, int DATA_SHARDS,int threadNum) throws IOException {

        IntegrityAuditing integrityAuditing = new IntegrityAuditing(filePath, BLOCK_SHARDS, DATA_SHARDS);
        String cosConfigFilePath = System.getProperty("user.dir")+"\\Properties";

        //prepare challengeData
        System.out.println("---Audit phase start---");
        ChallengeData challengeData = integrityAuditing.audit(460);


        FileInputStream propertiesFIS = new FileInputStream(cosConfigFilePath);
        Properties properties = new Properties();
        properties.load(propertiesFIS);
        propertiesFIS.close();
        String secretId = properties.getProperty("secretId");
        String secretKey = properties.getProperty("secretKey");
        String region = properties.getProperty("regionName");
        String bucketName = properties.getProperty("bucketName");
//
        long start_time_audit = System.nanoTime();
        TenYunControl tenYunControl=new TenYunControl();
        TenRequestClass tenRequestClass=new TenRequestClass(BLOCK_SHARDS - DATA_SHARDS,DATA_SHARDS,challengeData,bucketName,region,secretId,secretKey,threadNum);
        String input= JSON.toJSONString(tenRequestClass);
        String result=tenYunControl.invoke(input);
        long end_time_audit = System.nanoTime();
        time[3] = end_time_audit - start_time_audit;
        System.out.println("返回结果为"+result);
        ResponseClass responseClass=JSON.parseObject(result,ResponseClass.class);

//
        ProofData proofData = responseClass.proofData;
        long proofTime =responseClass.proofTime;
        long download_time=responseClass.download_time;
        System.out.println("proofTime"+proofTime);
        System.out.println("download_time"+download_time);
//

        String  proofDataStoragePath = "E:\\project\\file\\proofData.txt";
        File proofDataCost = new File(proofDataStoragePath);
        proofDataCost.createNewFile();
        OutputStream osProofData = new FileOutputStream(proofDataCost, false);
        osProofData.write(proofData.parityProof);
        osProofData.write(proofData.dataProof);
        osProofData.close();

        //cal communication cost
        long proofDataSize = proofDataCost.length();
        System.out.println("proofDataSize is " + proofDataSize + " Bytes");


        //execute verify parse
        System.out.println("---Verify phase start---");
        long start_time_verify = System.nanoTime();
        System.out.println(JSON.toJSONString(proofData));
        if (integrityAuditing.verify(challengeData, proofData)) {
            System.out.println("---Verify phase finished---");
            System.out.println("The data is intact in the cloud.The auditing process is success!");
        }
        else {
            System.out.println("The data was changed");
        }
        long end_time_verify = System.nanoTime();
        time[4] = end_time_verify - start_time_verify;
    }
}
