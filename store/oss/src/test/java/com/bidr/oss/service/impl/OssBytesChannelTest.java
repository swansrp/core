package com.bidr.oss.service.impl;

import com.bidr.kernel.exception.ServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Title: OssBytesChannelTest
 * Description: 按对象名直写/直读字节通道（{@code putBytes}/{@code readBytes}）的真机往返。
 * 本机实现是四家里唯一不需要云凭证的，故作为整条通用通道的代表腿；
 * 另两家（阿里云/火山）与 MinIO 同形，以代码审查 + 部署形态说明覆盖。
 *
 * <p>验三件事：① 字节原样来回且父目录自建（服务端产物落库的主路径）；
 * ② 对象名逃逸上传根目录必须拒（本机实现＝本地文件系统读写权限，这道闸门是安全性的本体）；
 * ③ 对象不存在要报得出来，调用方要能分清"没有这个对象"与"通道坏了"。</p>
 *
 * @author sharp
 * @since 2026-10-02
 */
class OssBytesChannelTest {

    /** 测试专用桶名：本机实现把对象落在 /oss/&lt;bucket&gt;/，用可整体删除的独立目录，不碰业务桶 */
    private static final String BUCKET = "core-oss-junit-bytes-channel";

    private OssLocalServiceImpl local;
    private Path root;

    @BeforeEach
    void setUp() {
        local = new OssLocalServiceImpl();
        ReflectionTestUtils.setField(local, "bucketName", BUCKET);
        ReflectionTestUtils.setField(local, "endpoint", "http://127.0.0.1");
        root = Paths.get("/oss", BUCKET).toAbsolutePath().normalize();
    }

    @AfterEach
    void cleanUp() throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toArray(Path[]::new)) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Test
    @DisplayName("直写直读：字节原样回来，父目录自动建，同 key 可覆盖")
    void roundTripsBytes() {
        byte[] payload = "inkhub-m12-bytes-channel".getBytes(StandardCharsets.UTF_8);
        String key = "skill/ai-draft-1/v1.zip";

        local.putBytes(key, payload, "application/zip");
        assertTrue(Files.exists(root.resolve(key)), "嵌套父目录应自动创建: " + root.resolve(key));
        assertArrayEquals(payload, local.readBytes(key), "回读字节必须逐字节相等");

        // 版本化产物重传＝覆盖同一 key，不产生第二份
        byte[] again = "v1-repacked".getBytes(StandardCharsets.UTF_8);
        local.putBytes(key, again, "application/zip");
        assertArrayEquals(again, local.readBytes(key), "同 key 重写必须覆盖而非追加");
    }

    @Test
    @DisplayName("对象名逃逸上传根目录：读写都拒，且没在根外落任何文件")
    void rejectsEscapingObjectName() {
        assertThrows(ServiceException.class,
                () -> local.putBytes("../evil.zip", new byte[]{1}, null), "写侧逃逸必须拒");
        assertThrows(ServiceException.class,
                () -> local.readBytes("../../evil.txt"), "读侧逃逸必须拒");
        assertThrows(ServiceException.class,
                () -> local.putBytes("skill/../../evil.zip", new byte[]{1}, null), "中段穿越也必须拒");

        assertFalse(Files.exists(root.getParent().resolve("evil.zip")),
                "被拒的 key 不得在根目录之外留下文件");
    }

    @Test
    @DisplayName("对象不存在要报出对象名（调用方据此分'没有'与'坏了'）")
    void reportsMissingObject() {
        ServiceException error = assertThrows(ServiceException.class,
                () -> local.readBytes("skill/never-written/v9.zip"));
        assertTrue(String.valueOf(error.getMessage()).contains("skill/never-written/v9.zip"),
                "错误消息要带对象名，实得：" + error.getMessage());
    }
}
