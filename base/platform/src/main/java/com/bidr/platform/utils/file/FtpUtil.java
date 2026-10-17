package com.bidr.platform.utils.file;

import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.validate.Validator;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.validate.Validator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;

import java.io.*;

/**
 * Title: FtpUtil
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2019/12/1 15:14
 * @description Project Name: Grote
 * @Package: com.srct.service.utils
 */
@Slf4j
public class FtpUtil {
    public static void upload(String ftpUrl, String ftpUserName, String ftpPassword, File file, String remoteDir,
                              String originName) {
        FTPClient ftpClient = null;
        InputStream inputStream = null;
        try {
            ftpClient = connectFtpServer(ftpUrl, ftpUserName, ftpPassword);
            if (ftpClient == null) {
                return;
            }
            //进入到文件保存的目录
            Boolean isSuccess = ftpClient.changeWorkingDirectory(remoteDir);
            if (!isSuccess) {
                ftpClient.makeDirectory(remoteDir);
                isSuccess = ftpClient.changeWorkingDirectory(remoteDir);
            }
            log.debug("切换目录{}{}", remoteDir, isSuccess ? "成功" : "失败");
            //保存文件
            inputStream = new FileInputStream(file);
            isSuccess = ftpClient.storeFile(originName, inputStream);
            log.debug("{}/{}---》上传{}！", remoteDir, originName, isSuccess ? "成功" : "失败");
            ftpClient.logout();
        } catch (IOException e) {
            Validator.assertException(ErrCodeSys.SYS_ERR_MSG, "上传失败");
        } finally {
            if (ftpClient.isConnected()) {
                try {
                    ftpClient.disconnect();
                } catch (IOException e) {
                    log.error("disconnect fail ------->>>{}", e.getCause());
                }
            }

            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    log.error("InputStream close fail ------->>>{}", e.getCause());
                }
            }
        }
    }

    private static FTPClient connectFtpServer(String ftpUrl, String ftpUserName, String ftpPassword) {
        FTPClient ftpClient = new FTPClient();
        //设置连接超时时间
        ftpClient.setConnectTimeout(1000 * 30);


        //设置被动模式，文件传输端口设置
        ftpClient.enterLocalPassiveMode();
        try {
            ftpClient.connect(ftpUrl);
            ftpClient.login(ftpUserName, ftpPassword);
            setDefaultConfig(ftpClient);
            int replyCode = ftpClient.getReplyCode();
            if (!FTPReply.isPositiveCompletion(replyCode)) {
                log.error("连接 ftp {} 失败", ftpUrl);
                ftpClient.disconnect();
                return null;
            }
            log.debug("replyCode==========={}", replyCode);
        } catch (IOException e) {
            log.error("连接失败 ------->>>{}", e.getCause());
            return null;
        }
        return ftpClient;
    }

    private static void setDefaultConfig(FTPClient ftpClient) throws IOException {
        //设置被动模式，文件传输端口设置
        ftpClient.enterLocalPassiveMode();
        //设置文件传输模式为二进制，可以保证传输的内容不会被改变
        ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
        ftpClient.setFileTransferMode(FTP.STREAM_TRANSFER_MODE);
        ftpClient.setRemoteVerificationEnabled(false);
        //设置ftp字符集
        ftpClient.setControlEncoding("utf-8");
    }

    public static void download(String ftpUrl, String ftpUserName, String ftpPassword, File file, String remoteDir,
                                String remoteFileName) {

        FTPClient ftpClient = null;
        OutputStream outputStream = null;
        try {
            ftpClient = connectFtpServer(ftpUrl, ftpUserName, ftpPassword);
            if (ftpClient == null) {
                return;
            }
            ftpClient.changeWorkingDirectory(remoteDir);
            FTPFile[] ftpFiles = ftpClient.listFiles(remoteDir);
            Boolean flag = false;
            //遍历当前目录下的文件，判断是否存在待下载的文件
            for (FTPFile ftpFile : ftpFiles) {
                if (ftpFile.getName().equals(remoteFileName)) {
                    flag = true;
                    break;
                }
            }

            if (!flag) {
                log.error("目录：{}下没有 {}", remoteDir, remoteFileName);
                return;
            }
            outputStream = new FileOutputStream(file);
            //下载文件
            Boolean isSuccess = ftpClient.retrieveFile(remoteFileName, outputStream);
            log.debug("下载文件 【{}】 {}}", remoteFileName, isSuccess ? "成功" : "失败");
            ftpClient.logout();
        } catch (IOException e) {
            log.error("下载文件 【{}】 失败", remoteFileName);
            return;
        } finally {
            if (ftpClient.isConnected()) {
                try {
                    ftpClient.disconnect();
                } catch (IOException e) {
                    log.error("disconnect fail ------->>>{}", e.getCause());
                }
            }
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    log.error("outputStream close fail ------->>>{}", e.getCause());
                }
            }
        }
    }
}
