package com.techfocus.util;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.log4j.Logger;

/**
 * 文件系统目录和文件监控服务
 * @author jack
 */
public class DirectoryWatcher {
    private static final Logger LOGGER = Logger.getLogger(DirectoryWatcher.class);
    
    private WatchService watchService = null;
    private final Map<WatchKey, Path> directories = new HashMap<>();
    private static ExecutorService EXECUTOR_SERVICE = null;
    private WatchEvent.Kind<?>[] events;
    
    public static DirectoryWatcher getDirectoryWatcher(final WatcherCallback watcherCallback
    		, WatchEvent.Kind<?>... events){
    	return new DirectoryWatcher(watcherCallback, events);
    }
    private DirectoryWatcher(){}
    private DirectoryWatcher(final WatcherCallback watcherCallback, WatchEvent.Kind<?>... events){
        try {
            if(events.length == 0){
                throw new RuntimeException("必须至少指定一个监控的事件，如："
                		+ "StandardWatchEventKinds.ENTRY_CREATE, "
                		+ "StandardWatchEventKinds.ENTRY_MODIFY, "
                		+ "StandardWatchEventKinds.ENTRY_DELETE");
            }
            synchronized(DirectoryWatcher.class){
                if(EXECUTOR_SERVICE == null){
                    EXECUTOR_SERVICE = Executors.newCachedThreadPool();
                }
            }
            this.events = new WatchEvent.Kind<?>[events.length];
            int i=0;
            for(WatchEvent.Kind<?> event : events){
                this.events[i++] = event;
                LOGGER.info("注册事件："+event.name());
            }
            watchService = FileSystems.getDefault().newWatchService();
            EXECUTOR_SERVICE.submit(new Runnable(){

                @Override
                public void run() {
                    watch(watcherCallback);
                }

            });
        } catch (IOException ex) {
            LOGGER.error("构造文件系统监控服务失败：", ex);
            throw new RuntimeException(ex);
        }
    }
    /**
     * 监控指定目录，不监控子目录
     * @param path 
     */
    public void watchDirectory(String path) {
        watchDirectory(Paths.get(path));
    }
    /**
     * 监控指定目录，不监控子目录
     * @param path 
     */
    public void watchDirectory(Path path) {
        registerDirectory(path);
    }
    /**
     * 监控指定的目录及其所有子目录
     * @param path 
     */
    public void watchDirectoryTree(String path) {
        watchDirectoryTree(Paths.get(path));
    }
    /**
     * 监控指定的目录及其所有子目录
     * @param path 
     */
    public void watchDirectoryTree(Path path) {
        registerDirectoryTree(path);
    }
    /**
     * 关闭监控线程
     */
    public void close(){
        EXECUTOR_SERVICE.shutdown();
    }
    /**
     * 监控事件分发器
     * @param watcherCallback 事件回调
     */
    @SuppressWarnings("unchecked")
	private void watch(WatcherCallback watcherCallback){
        try {
            while (true) {
                final WatchKey key = watchService.take();
                if(key == null){
                    continue;
                }
                for (WatchEvent<?> watchEvent : key.pollEvents()) {
                    final WatchEvent.Kind<?> kind = watchEvent.kind();
                    //忽略无效事件
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }
                    final WatchEvent<Path> watchEventPath = (WatchEvent<Path>) watchEvent;
                    //path是相对路径（相对于监控目录）
                    final Path contextPath = watchEventPath.context();
                    LOGGER.info("contextPath:"+contextPath);
                    //获取监控目录
                    final Path directoryPath = directories.get(key);
                    LOGGER.info("directoryPath:"+directoryPath);
                    //得到绝对路径
                    final Path absolutePath = directoryPath.resolve(contextPath);
                    LOGGER.info("absolutePath:"+absolutePath);
                    LOGGER.info("kind:"+kind);
                    //判断事件类别
                    switch (kind.name()) {
                        case "ENTRY_CREATE":
                            if (Files.isDirectory(absolutePath, LinkOption.NOFOLLOW_LINKS)) {
                                LOGGER.info("新增目录：" + absolutePath);
                                //为新增的目录及其所有子目录注册监控事件
                                registerDirectoryTree(absolutePath);
                            }else{
                                LOGGER.info("新增文件：" + absolutePath);
                            }
                            break;
                        case "ENTRY_DELETE":
                            LOGGER.info("删除：" + absolutePath);
                            break;
                        case "ENTRY_MODIFY":
                            LOGGER.info("修改：" + absolutePath);
                            break;
                    }
                    //业务逻辑
                    watcherCallback.execute(kind, absolutePath.toAbsolutePath().toString());
                }
                boolean valid = key.reset();
                if (!valid) {
                    if(directories.get(key) != null){
                        LOGGER.info("停止监控目录："+directories.get(key));
                        directories.remove(key);
                    }
                }
            }
        } catch (InterruptedException ex) {
            LOGGER.info("监控目录线程退出");
        } finally{
            try {
                watchService.close();
                LOGGER.info("关闭监控目录服务");
            } catch (IOException ex) {
                LOGGER.error("关闭监控目录服务出错", ex);
            }
        }
    }
    /**
     * 为指定目录及其所有子目录注册监控事件
     * @param path 目录
     */
    private void registerDirectoryTree(Path path) {
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                        throws IOException {
                    registerDirectory(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ex) {
            LOGGER.error("监控目录失败：" + path.toAbsolutePath(), ex);
        }
    }
    /**
     * 为指定目录注册监控事件
     * @param path
     */
    private void registerDirectory(Path path) {
        try {
            LOGGER.info("监控目录:" + path);
            WatchKey key = path.register(watchService, events);
            directories.put(key, path);
        } catch (IOException ex) {
            LOGGER.error("监控目录失败：" + path.toAbsolutePath(), ex);
        }
    }
//    public static void main(String[] args) {
//        DirectoryWatcher dictionaryWatcher = new DirectoryWatcher(new WatcherCallback(){
//            private long lastExecute = System.currentTimeMillis();
//            @Override
//            public void execute(WatchEvent.Kind<?> kind, String path) {
//                if(System.currentTimeMillis() - lastExecute > 1000){                  
//                    lastExecute = System.currentTimeMillis();
//                    //刷新词典
//                    System.out.println("事件："+kind.name()+" ,路径："+path);
//                }
//            }
//        }, StandardWatchEventKinds.ENTRY_CREATE,
//                    StandardWatchEventKinds.ENTRY_MODIFY,
//                    StandardWatchEventKinds.ENTRY_DELETE);
//        //监控DIC目录及其所有子目录的子目录...递归
//        dictionaryWatcher.watchDirectoryTree("d:/DIC");
//        //只监控DIC2目录
//        dictionaryWatcher.watchDirectory("d:/DIC2");
//    }
    public static interface WatcherCallback{
        public void execute(WatchEvent.Kind<?> kind, String path);
    }
}