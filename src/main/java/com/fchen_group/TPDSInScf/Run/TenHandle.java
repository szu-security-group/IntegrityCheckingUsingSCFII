package com.fchen_group.TPDSInScf.Run;

import com.alibaba.fastjson.JSON;
import com.fchen_group.TPDSInScf.Core.ChallengeData;
import com.fchen_group.TPDSInScf.Core.IntegrityAuditing;
import com.fchen_group.TPDSInScf.Core.ProofData;
import com.fchen_group.TPDSInScf.Utils.CloudAPI;
import com.fchen_group.TPDSInScf.Utils.ResponseClass;
import com.fchen_group.TPDSInScf.Utils.TenRequestClass;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TenHandle {
    public String mainHandler(TenRequestClass request) {
        ChallengeData challengeData =request.challengeData;
        String bucketName = request.bucketName;
        String regionName = request.regionName;
        int DATA_SHARDS = request.DaTA_SHARDS;
        int PARITY_SHARDS = request.PARITY_SHARDS;
        String secretId = request.secretId;
        String secretKey = request.secretKey;
        int threadNum = request.threadNum;
        //拿S3存储桶里的数据

        CloudAPI yunAPI = new CloudAPI(secretId, secretKey, regionName, bucketName);
//get ProofData from cloud by using challengeData from cloud
        IntegrityAuditing integrityAuditing = new IntegrityAuditing(DATA_SHARDS, PARITY_SHARDS);
        byte[][] downloadData = new byte[challengeData.index.length][DATA_SHARDS];
        byte[][] downloadParity = new byte[challengeData.index.length][PARITY_SHARDS];
        // 创建一个固定大小的线程池，线程数与处理器核心数相同
        long start_time_download =0;
        long end_time_download = 0;


        if(threadNum!=1){

            ExecutorService executor = Executors.newFixedThreadPool(threadNum);

            start_time_download = System.nanoTime();
            for (int i = 0; i < challengeData.index.length; i++) {
                final int index = i;
                executor.submit(() -> {
                    downloadData[index] = yunAPI.downloadPartFile("sourceFile.txt", challengeData.index[index] * DATA_SHARDS, DATA_SHARDS);
                    downloadParity[index] = yunAPI.downloadPartFile("parities.txt", challengeData.index[index] * PARITY_SHARDS, PARITY_SHARDS);
                });
            }

            // 关闭线程池并等待所有任务完成
            executor.shutdown();
            try {
                executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            end_time_download = System.nanoTime();

            System.out.println("down load from COS successfully");

        }else {
            start_time_download = System.nanoTime();
            for (int i = 0; i < challengeData.index.length; i++) {
                downloadData[i] = yunAPI.downloadPartFile("sourceFile.txt", challengeData.index[i] * DATA_SHARDS, DATA_SHARDS);
                downloadParity[i] = yunAPI.downloadPartFile("parities.txt", challengeData.index[i] * PARITY_SHARDS, PARITY_SHARDS);
            }
            end_time_download = System.nanoTime();
        }
        long start_time_proof = System.nanoTime();
        ProofData proofData = integrityAuditing.prove(challengeData, downloadData, downloadParity);
        long end_time_proof = System.nanoTime();

        ResponseClass responseClass=new ResponseClass(proofData);
        long download_time=end_time_download-start_time_download;
        long proofTime=end_time_proof-start_time_proof;

        System.out.println(download_time);
        System.out.println(proofTime);
        responseClass.download_time=download_time;
        responseClass.proofTime=proofTime;
        String output= JSON.toJSONString(responseClass);
        System.out.println("最终结果"+output);
        return output;
    }
    public static void main(String[] args) {

    }
}
