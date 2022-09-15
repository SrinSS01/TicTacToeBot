package com.tictactoebot;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import java.util.HashMap;
import java.util.Map;

public class Database {
    private static final MongoClient mongoClient = MongoClients.create("mongodb+srv://srin:dQum7hg9t6nNrza@cluster0.wk7r9tr.mongodb.net/?retryWrites=true&w=majority");
    private static final MongoDatabase database = mongoClient.getDatabase("tictactoe");
    public static final MongoCollection<org.bson.Document> LEVELS = database.getCollection("levels");
    public static final Map<Long, MatchData> MATCH_DATA_MAP = new HashMap<>();
}
