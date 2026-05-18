package com.g726;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ArchiveManager {
    private List<GameArchive> archives;
    private final Logger logger = LoggingManager.getLogger();

    public ArchiveManager() {
        this.archives = JsonManager.loadFromJson();
    }

    public List<GameArchive> getAllArchives() {
        return archives;
    }

    public void addArchive(String gameName, String saveName, String pathName) {
        GameArchive newArchive = new GameArchive(gameName, saveName, pathName);
        for (GameArchive existing : archives) {
            if (existing.getGameName().equalsIgnoreCase(newArchive.getGameName()) &&
                    !existing.getAbsRawSavePathString().equals(newArchive.getAbsRawSavePathString())) {
                String message = "错误：已存在同名游戏，请使用不同的名称！";
                logger.warning(message);
                throw new ArchiveOperationException(message);
            }
            if (existing.getGameName().equals(newArchive.getGameName()) &&
                    existing.getSaveName().equals(newArchive.getSaveName()) &&
                    existing.getAbsRawSavePathString().equals(newArchive.getAbsRawSavePathString())) {
                String message = "该存档已存在，添加失败！";
                logger.warning(message);
                throw new ArchiveOperationException(message);
            }
        }

        if (!newArchive.checkPath()) {
            String message = "错误：您填写的源存档路径不存在，添加失败！";
            logger.warning(message);
            throw new ArchiveOperationException(message);
        }

        try {
            Files.createDirectories(newArchive.getBackupPath());
            SomeUtils.copyDirectory(newArchive.getSavePath(), newArchive.getBackupPath());
            archives.add(newArchive);
            JsonManager.saveToJson(archives);

        } catch (IOException e) {
            logger.log(Level.SEVERE, "备份失败", e);
            throw new ArchiveOperationException("备份失败", e);
        }
    }

    public void updateArchive(String gameName, String saveName) {
        GameArchive template = null;

        for (int i = archives.size() - 1; i >= 0; i--) {
            GameArchive existing = archives.get(i);
            if (existing.getGameName().equalsIgnoreCase(gameName) &&
                    existing.getSaveName().equalsIgnoreCase(saveName)) {
                template = existing;
                break;
            }
        }

        if (template != null) {
            GameArchive newSnapshot = new GameArchive(template);

            if (template.getTimeStamp().equals(newSnapshot.getTimeStamp())) {
                logger.info("防抖拦截：已成功备份 (" + template.getTimeStamp() + ")");
                return; 
            }

            if (!newSnapshot.checkPath()) {
                String message = "错误：源文件已丢失，无法创建新快照！";
                logger.warning(message);
                throw new ArchiveOperationException(message);
            }

            try {
                Files.createDirectories(newSnapshot.getBackupPath());
                SomeUtils.copyDirectory(newSnapshot.getSavePath(), newSnapshot.getBackupPath());
                archives.add(newSnapshot);
                JsonManager.saveToJson(archives);

            } catch (IOException e) {
                logger.log(Level.SEVERE, "备份失败", e);
                throw new ArchiveOperationException("备份失败", e);
            }
        } else {
            String message = "未找到该存档，请确认名称是否正确";
            logger.warning(message);
            throw new ArchiveOperationException(message);
        }
    }

    public void removeArchive(String gameName, String saveName) {
        boolean removed = archives
                .removeIf(archive -> archive.getGameName().equals(gameName) && archive.getSaveName().equals(saveName));

        if (removed) {
            JsonManager.saveToJson(archives);

            Path targetDeletePath = Paths.get("Backup").resolve(gameName).resolve(saveName);
            try {
                SomeUtils.deleteDirectory(targetDeletePath);
                logger.info("已删除存档: " + gameName + saveName);
            } catch (IOException e) {
                logger.log(Level.SEVERE, "删除失败，文件可能被占用", e);
                throw new ArchiveOperationException("删除失败，文件可能被占用", e);
            }
        } else {
            String message = "未找到指定的存档，删除失败";
            logger.warning(message);
            throw new ArchiveOperationException(message);
        }
    }

    public void refreshArchives() {
        logger.info("正在扫描并校验文件...");

        int initialSize = archives.size();

        archives.removeIf(archive -> {
            boolean isMissing = Files.notExists(archive.getBackupPath());
            if (isMissing) {
                logger.warning("发现丢失文件: " + archive.getGameName() + archive.getSaveName() + archive.getTimeStamp());
            }
            return isMissing;
        });

        int removedCount = initialSize - archives.size();

        if (removedCount > 0) {
            logger.info("刷新完成：共清理了 " + removedCount + " 条失效的记录");
            JsonManager.saveToJson(archives);
        } else {
            logger.info("刷新完成：当前记录与文件相符");
        }
    }

    public boolean restoreArchive(String gameName, String saveName, String timeStamp) {
        GameArchive targetArchive = null;

        for (GameArchive archive : archives) {
            if (archive.getGameName().equals(gameName) &&
                archive.getSaveName().equals(saveName) &&
                archive.getTimeStamp().equals(timeStamp)) { 
                targetArchive = archive;
                break;
            }
        }

        if (targetArchive == null) {
            String message = "还原失败：未在记录中找到指定的存档版本 (" + timeStamp + ")";
            logger.warning(message);
            throw new ArchiveOperationException(message);
        }

        Path backupDir = targetArchive.getBackupPath();
        Path originalSaveDir = Paths.get(targetArchive.getAbsRawSavePathString()); 

        if (originalSaveDir.getParent() == null || originalSaveDir.getNameCount() <= 1) {
            String message = "严重安全拦截：源路径过短（疑似根目录），已强制中止删除操作以保护您的硬盘数据！";
            logger.severe(message);
            throw new ArchiveOperationException(message);
        }

        try {
            SomeUtils.deleteDirectory(originalSaveDir);
            Files.createDirectories(originalSaveDir);
            SomeUtils.copyDirectory(backupDir, originalSaveDir);
            
            logger.info("成功还原存档: " + gameName + saveName + " 至版本 [" + timeStamp + "]");
            return true;

        } catch (IOException e) {
            logger.log(Level.SEVERE, "还原过程中发生文件读写错误", e);
            throw new ArchiveOperationException("还原过程中发生文件读写错误", e);
        }
    }

    public void removeSnapshot(String gameName, String saveName, String timeStamp) {
        boolean removed = archives.removeIf(archive -> 
            archive.getGameName().equals(gameName) && 
            archive.getSaveName().equals(saveName) && 
            archive.getTimeStamp().equals(timeStamp)
        );

        if (removed) {
            JsonManager.saveToJson(archives);
            Path targetDeletePath = Paths.get("Backup").resolve(gameName).resolve(saveName).resolve(timeStamp);
            try {
                SomeUtils.deleteDirectory(targetDeletePath);
                logger.info("已删除特定快照: " + gameName +  saveName + timeStamp);
            } catch (IOException e) {
                logger.log(Level.SEVERE, "删除特定快照失败", e);
                throw new ArchiveOperationException("删除特定快照失败", e);
            }
        } else {
            String message = "未找到指定的时间戳快照，删除失败";
            logger.warning(message);
            throw new ArchiveOperationException(message);
        }
    }

    public void enforceHistoryLimit(String gameName, String saveName, int maxHistory) {
        if (maxHistory <= 0) return;

        List<GameArchive> branchSnapshots = new java.util.ArrayList<>();
        for (GameArchive archive : archives) {
            if (archive.getGameName().equals(gameName) && archive.getSaveName().equals(saveName)) {
                branchSnapshots.add(archive);
            }
        }

        if (branchSnapshots.size() > maxHistory) {
            branchSnapshots.sort((a, b) -> a.getTimeStamp().compareTo(b.getTimeStamp()));

            int numToRemove = branchSnapshots.size() - maxHistory;
            for (int i = 0; i < numToRemove; i++) {
                GameArchive oldest = branchSnapshots.get(i);
                logger.info("触发最大历史限制，自动清理最老快照...");
                removeSnapshot(oldest.getGameName(), oldest.getSaveName(), oldest.getTimeStamp());
            }
        }
    }

    public void createBranchFromSnapshot(String gameName, String sourceSaveName, String timeStamp, String newBranchName) {
        GameArchive targetArchive = null;
        for (GameArchive archive : archives) {
            if (archive.getGameName().equals(gameName) &&
                archive.getSaveName().equals(sourceSaveName) &&
                archive.getTimeStamp().equals(timeStamp)) {
                targetArchive = archive;
                break;
            }
        }

        if (targetArchive == null) {
            String message = "创建分支失败：未找到指定的快照";
            logger.warning(message);
            throw new ArchiveOperationException(message);
        }

        for (GameArchive archive : archives) {
            if (archive.getGameName().equals(gameName) && archive.getSaveName().equals(newBranchName)) {
                String message = "创建分支失败：分支名 [" + newBranchName + "] 已存在！";
                logger.warning(message);
                throw new ArchiveOperationException(message);
            }
        }

        GameArchive newBranchArchive = new GameArchive(gameName, newBranchName, targetArchive.getAbsRawSavePathString(), targetArchive.getTimeStamp());

        try {
            Files.createDirectories(newBranchArchive.getBackupPath());
            SomeUtils.copyDirectory(targetArchive.getBackupPath(), newBranchArchive.getBackupPath());
            
            archives.add(newBranchArchive);
            JsonManager.saveToJson(archives);
            logger.info("成功基于快照 [" + timeStamp + "] 创建新分支: " + newBranchName);

        } catch (IOException e) {
            logger.log(Level.SEVERE, "创建分支时发生错误", e);
            throw new ArchiveOperationException("创建分支时发生错误", e);
        }
    }

    public void createBranchCopy(String gameName, String sourceSaveName, String newBranchName) {
        for (GameArchive archive : archives) {
            if (archive.getGameName().equals(gameName) && archive.getSaveName().equals(newBranchName)) {
                String message = "创建副本失败：分支名 [" + newBranchName + "] 已存在！";
                logger.warning(message);
                throw new ArchiveOperationException(message);
            }
        }

        List<GameArchive> newArchivesToAdd = new java.util.ArrayList<>();
        for (GameArchive archive : archives) {
            if (archive.getGameName().equals(gameName) && archive.getSaveName().equals(sourceSaveName)) {
                GameArchive clonedArchive = new GameArchive(gameName, newBranchName, archive.getAbsRawSavePathString(), archive.getTimeStamp());
                try {
                    Files.createDirectories(clonedArchive.getBackupPath());
                    SomeUtils.copyDirectory(archive.getBackupPath(), clonedArchive.getBackupPath());
                    newArchivesToAdd.add(clonedArchive);
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "复制快照失败", e);
                    throw new ArchiveOperationException("复制快照失败", e);
                }
            }
        }

        if (!newArchivesToAdd.isEmpty()) {
            archives.addAll(newArchivesToAdd);
            JsonManager.saveToJson(archives);
            logger.info("成功创建分支副本: " + newBranchName);
        }
    }

    public void renameSnapshot(String gameName, String saveName, String timeStamp, String newName) {
        for (GameArchive archive : archives) {
            if (archive.getGameName().equals(gameName) &&
                archive.getSaveName().equals(saveName) &&
                archive.getTimeStamp().equals(timeStamp)) {
                
                archive.setCustomName(newName);
                JsonManager.saveToJson(archives);
                logger.info("成功为快照 [" + timeStamp + "] 设置标签: " + newName);
                return;
            }
        }
    }
}