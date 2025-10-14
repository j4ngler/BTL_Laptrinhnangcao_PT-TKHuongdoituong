package com.example.docmgmt.repo;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;
import org.bson.types.ObjectId;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class GridFsRepository implements AutoCloseable {
    private final MongoClient mongoClient;
    private final String dbName;
    private final String bucketName;

    public GridFsRepository(MongoClient client, String dbName, String bucketName) {
        this.mongoClient = client;
        this.dbName = dbName;
        this.bucketName = bucketName;
    }

    private GridFSBucket bucket() {
        MongoDatabase db = mongoClient.getDatabase(dbName);
        return GridFSBuckets.create(db, bucketName);
    }

    public String upload(Path path, String title) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            GridFSUploadOptions opts = new GridFSUploadOptions();
            ObjectId id = bucket().uploadFromStream(title, in, opts);
            return id.toHexString();
        }
    }

    public void download(String hexId, Path outPath) throws IOException {
        ObjectId id = new ObjectId(hexId);
        Files.createDirectories(outPath.getParent());
        try (OutputStream os = Files.newOutputStream(outPath)) {
            bucket().downloadToStream(id, os);
        }
    }

    public String saveFile(String filename, InputStream inputStream) throws IOException {
        GridFSUploadOptions opts = new GridFSUploadOptions();
        ObjectId id = bucket().uploadFromStream(filename, inputStream, opts);
        return id.toHexString();
    }

    @Override
    public void close() {
        // no-op (client managed by Config)
    }
}

