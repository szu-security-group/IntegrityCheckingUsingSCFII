package com.fchen_group.TPDSInScf.Utils;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.BasicSessionCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.model.*;
import com.qcloud.cos.region.Region;


import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Provide a unified interface to access the cloud object storage
 * This class have two method:    uploadFile, downloadPartFile
 */

public class CloudAPI {

    private COSClient cosClient;
    private String bucketName;
    private String regionName;


    /**
     * Initial a COS client with configuration file path
     * used in Client
     *
     * @param cosConfigFilePath configure file path
     */
    public CloudAPI(String cosConfigFilePath) {
        String secretId = null;
        String secretKey = null;


        //read configuration file
        try {
            FileInputStream propertiesFIS = new FileInputStream(cosConfigFilePath);
            Properties properties = new Properties();
            properties.load(propertiesFIS);
            propertiesFIS.close();

            secretId = properties.getProperty("secretId");
            secretKey = properties.getProperty("secretKey");
            regionName = properties.getProperty("regionName");
            bucketName = properties.getProperty("bucketName");

        } catch (IOException e) {
            e.printStackTrace();
        }

        //check whether thr variables read successfully
        assert secretId != null;
        assert secretKey != null;
        assert regionName != null;
        assert bucketName != null;

        //initial  operation
        COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
        Region region = new Region(regionName);
        ClientConfig clientConfig = new ClientConfig(region);
        cosClient = new COSClient(cred, clientConfig);
    }

    /**
     * Initial a COS client with param
     * used in SCF
     *
     * @param secretId
     * @param secretKey
     * @param regionName
     * @param bucketName
     */
    public CloudAPI(String secretId, String secretKey, String regionName, String bucketName) {
        this.bucketName = bucketName;
        this.regionName = regionName;

        //initial  operation
        COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
        Region region = new Region(regionName);
        ClientConfig clientConfig = new ClientConfig(region);
        cosClient = new COSClient(cred, clientConfig);

        //Use temporary key pair and token, the initialization method is different
       /*
        BasicSessionCredentials cred = new BasicSessionCredentials(secretId, secretKey, sessionToken);
        Region region = new Region(regionName);
        ClientConfig clientConfig = new ClientConfig(region);
        COSClient cosClient = new COSClient(cred, clientConfig);
        System.out.println("creat cosClient successfully ");*/
    }

    /**
     * upload data to cloud object storage
     *
     * @param localFilePath of data to be uploaded
     * @param cloudFileName the file name of data in the cloud object storage
     */
    public void uploadFile(String localFilePath, String cloudFileName) {
        File localFile = new File(localFilePath);
        PutObjectResult putObjectResult = cosClient.putObject(bucketName, cloudFileName, localFile);
    }

    /**
     * down file from COS from the specified location
     */
    public byte[] downloadPartFile(String cloudFileName, long startPos, int length) {
        COSObject cosObject;

        GetObjectRequest getObjectRuquest = new GetObjectRequest(bucketName, cloudFileName);
        byte[] fileBlock = new byte[length];

        //down file by block format
        getObjectRuquest.setRange(startPos, startPos + length - 1);
        cosObject = cosClient.getObject(getObjectRuquest);
        InputStream cloudFileIn = cosObject.getObjectContent();

        try {
            for (int n = 0; n != -1; ) {
                n = cloudFileIn.read(fileBlock, 0, fileBlock.length);
            }
            cloudFileIn.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return fileBlock;
    }

    public void multipartUpload(String filePath, int partCount, String cloudfileName) {
        File file = new File(filePath);
        long fileSize = file.length();
        long partSize = fileSize / partCount;

        // 初始化分块上传
        InitiateMultipartUploadRequest initRequest = new InitiateMultipartUploadRequest(bucketName, cloudfileName);
        InitiateMultipartUploadResult initResult = cosClient.initiateMultipartUpload(initRequest);
        String uploadId = initResult.getUploadId();

        CountDownLatch latch = new CountDownLatch(partCount);

        List<PartETag> partETags = new ArrayList<>();
        try {
            ExecutorService executorService = Executors.newFixedThreadPool(partCount);
            for (int j = 0; j < partCount; j++) {
                int i = j;
                executorService.execute(() ->
                {
                    long startPos = i * partSize;
                    long curPartSize = (i + 1 == partCount) ? (fileSize - startPos) : partSize;
                    InputStream inputStream = null;
                    try {
                        inputStream = new FileInputStream(file);
                    } catch (FileNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                    try {
                        inputStream.skip(startPos);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    UploadPartRequest uploadRequest = new UploadPartRequest()
                            .withBucketName(bucketName)
                            .withKey(cloudfileName)
                            .withUploadId(uploadId)
                            .withInputStream(inputStream)
                            .withPartNumber(i + 1)
                            .withPartSize(curPartSize);
                    System.out.println("start" + i);

                    UploadPartResult uploadResult = cosClient.uploadPart(uploadRequest);
                    long time =System.nanoTime();
                    System.out.println("end" + i+"    "+time);
                    partETags.add(uploadResult.getPartETag());
                    latch.countDown();
                    System.out.println("finish" + i);
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            latch.await();
            executorService.shutdown();
            // 完成分块上传
            CompleteMultipartUploadRequest compRequest = new CompleteMultipartUploadRequest(bucketName, cloudfileName, uploadId, partETags);
            cosClient.completeMultipartUpload(compRequest);
            System.out.println("Multipart upload completed.");
        } catch (Exception e) {
            e.printStackTrace();
            // 如果发生异常，终止上传任务
            AbortMultipartUploadRequest abortRequest = new AbortMultipartUploadRequest(bucketName, cloudfileName, uploadId);
            cosClient.abortMultipartUpload(abortRequest);
        } finally {
            cosClient.shutdown();
        }
    }


    public static void main(String[] args) throws InterruptedException {
    }

}
