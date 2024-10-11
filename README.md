# Cloud Storage Auditing System Using Serverless ComputingII

Cloud storage, as a new type of computing infrastructure, greatly facilitates users in building application systems, but at the same time, it also brings data security issues.
Although existing cloud storage audit solutions have made significant progress in theory, they still face challenges of high cost and low efficiency in practical applications. For this reason, this
The article proposes a cloud storage audit system based on Serverless Cloud Function (SCF), which is more efficient than the current one
There are plans that promote the practical availability of cloud storage audit technology from three aspects. Firstly, a parallel data based on multiple TCP connections was proposed
The cloud based solution has improved the operational efficiency of the cloud storage audit system. Secondly, a method was proposed to increase cloud resource utilization while reducing SCF execution
The method of time has reduced the cost of cloud storage auditing. Thirdly, an automated method for deploying cloud service systems has been proposed, which improves cloud storage auditing
The usability of the system. The experimental results show that the system performs excellently in large-scale data auditing tasks: this solution can be applied in existing cloud service environments
Rapid deployment in the environment, compared to existing solutions, the audit time is only 10% of the original system, and the cost of each audit is 5% of the original system. Therefore, this system
The practical application of the unified cloud storage audit solution provides strong support, and the technology proposed in this article has reference significance for serverless computing applications.

SCF, as a computing interface for object storage, involves three parties: users, storage services, and computing services. The SCF based cloud storage audit system includes the following steps:

<div align="center">
    <img src="mdPics/System2.png" alt="System2" style="zoom:50%;" />
</div>

## Build


We developed this project using Java 1.8.0_202, Tencent Cloud SDK Java, and compiled it using IntelliJ IDEA and Maven. The dependencies related to Tencent Cloud use different versions. For more detailed configuration information, please refer to the pom.xml file of the project

Please set the parameters in the Properties file to grant the system access to OSS, call SCF services, and set the location for generating intermediate files during auditing.


## Usage

Firstly, please import this project into IntelliJ IDEA, which will automatically import all dependencies in pom.xml. Please pay attention to the mirrored source to smoothly download these dependencies.

Secondly, you must prepare cloud object storage services and serverless cloud functionality services in Tencent Cloud or other cloud service providers. You need to have an account and activate these two services. The project will be self packaged and deployed, you just need to set up the Properties file.

In the Properties file:

The configuration in the above section is cloud related, and you need to provide credentials for accessing the cloud.

The middle part is related to operation. You need to set the path of Maven and provide a temporary space to save the files of the system's intermediate operations. These temporary files can be deleted after execution. Note that the filename refers to the local path you want to audit

The following section contains some default parameters that do not need to be modified. It can ensure the efficient operation of the system on the cloud.

## Contributing

Please feel free to crack our audit system at any time, we are willing to communicate together.
