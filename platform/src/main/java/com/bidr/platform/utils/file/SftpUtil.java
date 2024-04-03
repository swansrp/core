package com.bidr.platform.utils.file;

import com.jcraft.jsch.*;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Vector;

/**
 * @author Sharp
 */

@Slf4j
public class SftpUtil {

    /**
     * 上传文件
     *
     * @param directory  上传的目录
     * @param uploadFile 要上传的文件
     * @param sftpConfig
     */
    public static void upload(String directory, String uploadFile, SftpConfig sftpConfig) {
        ChannelSftp sftp = connect(sftpConfig);
        try {
            sftp.cd(directory);
        } catch (SftpException e) {
            try {
                sftp.mkdir(directory);
                sftp.cd(directory);
            } catch (SftpException e1) {
                throw new RuntimeException("ftp创建文件路径失败" + directory);
            }
        }
        File file = new File(uploadFile);
        InputStream inputStream = null;
        try {
            inputStream = Files.newInputStream(file.toPath());
            sftp.put(inputStream, file.getName());
            log.info("传输文件{}成功", file.getName());
        } catch (Exception e) {
            throw new RuntimeException("sftp异常" + e);
        } finally {
            disConnect(sftp);
            closeStream(inputStream, null);
        }

    }

    public static ChannelSftp connect(SftpConfig sftpConfig) {
        ChannelSftp sftp = null;
        try {
            JSch jsch = new JSch();
            jsch.getSession(sftpConfig.getUsername(), sftpConfig.getHostname(), sftpConfig.getPort());
            Session sshSession = jsch.getSession(sftpConfig.getUsername(), sftpConfig.getHostname(), sftpConfig.getPort());
            log.info("Session created ... UserName=" + sftpConfig.getUsername() + ";host=" + sftpConfig.getHostname() + ";port=" + sftpConfig.getPort());
            sshSession.setPassword(sftpConfig.getPassword());
            Properties sshConfig = new Properties();
            sshConfig.put("StrictHostKeyChecking", "no");
            sshSession.setConfig(sshConfig);
            sshSession.connect();
            log.info("Session connected ...");
            log.info("Opening Channel ...");
            Channel channel = sshSession.openChannel("sftp");
            channel.connect();
            sftp = (ChannelSftp) channel;
            log.info("登录成功");
        } catch (Exception e) {
            log.info("登录失败");
        }
        return sftp;
    }

    public static void disConnect(ChannelSftp sftp) {
        try {
            sftp.disconnect();
            sftp.getSession().disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void closeStream(InputStream inputStream, OutputStream outputStream) {
        if (outputStream != null) {
            try {
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 下载文件
     *
     * @param directory    下载目录
     * @param downloadFile 下载的文件
     * @param saveFile     存在本地的路径
     * @param sftpConfig
     */
    public static void download(String directory, String downloadFile, String saveFile, SftpConfig sftpConfig) {
        OutputStream output = null;
        try {
            File localDirFile = new File(saveFile);
            // 判断本地目录是否存在，不存在需要新建各级目录
            if (!localDirFile.exists()) {
                localDirFile.mkdirs();
            }
            log.info("开始获取远程文件:[{}]---->[{}]", directory, saveFile);

            ChannelSftp sftp = connect(sftpConfig);
            sftp.cd(directory);
            log.info("打开远程文件:[{}]", directory);

            output = Files.newOutputStream(new File(saveFile.concat(File.separator).concat(downloadFile)).toPath());
            sftp.get(downloadFile, output);
            log.info("文件下载成功");
            disConnect(sftp);
        } catch (Exception e) {
            log.info("文件下载出现异常，[{}]", e);
            throw new RuntimeException("文件下载出现异常，[{}]", e);
        } finally {
            closeStream(null, output);
        }
    }

    /**
     * 删除文件
     *
     * @param directory  要删除文件所在目录
     * @param deleteFile 要删除的文件
     * @param sftp
     */
    public static void delete(String directory, String deleteFile, ChannelSftp sftp) {
        try {
            sftp.cd(directory);
            sftp.rm(deleteFile);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 列出目录下的文件
     *
     * @param directory  要列出的目录
     * @param sftpConfig
     * @return
     * @throws SftpException
     */
    public static List<String> listFiles(String directory, SftpConfig sftpConfig) throws SftpException {
        ChannelSftp sftp = connect(sftpConfig);
        List<String> fileNameList = new ArrayList<>();
        try {
            sftp.cd(directory);
        } catch (SftpException e) {
            return fileNameList;
        }
        Vector vector = sftp.ls(directory);
        for (int i = 0; i < vector.size(); i++) {
            if (vector.get(i) instanceof ChannelSftp.LsEntry) {
                ChannelSftp.LsEntry lsEntry = (ChannelSftp.LsEntry) vector.get(i);
                String fileName = lsEntry.getFilename();
                if (".".equals(fileName) || "..".equals(fileName)) {
                    continue;
                }
                fileNameList.add(fileName);
            }
        }
        disConnect(sftp);
        return fileNameList;
    }


}
