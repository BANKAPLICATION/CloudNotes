package com.cloudnotes.app.database;

import android.util.Log;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBMapper;
import com.amazonaws.mobileconnectors.dynamodbv2.dynamodbmapper.DynamoDBQueryExpression;
import com.cloudnotes.app.entities.Note;

import java.util.ArrayList;
import java.util.List;

public class DynamoDBHelper {
    private final DynamoDBMapper dynamoDBMapper;

    public DynamoDBHelper(DynamoDBMapper dynamoDBMapper) {
        this.dynamoDBMapper = dynamoDBMapper;
    }

    // Insert a new note
    public void insertNote(Note note, String userId) {
        note.setUserId(userId);
        // Get next available ID and set it
        int nextId = getNextId(userId);
        note.setId(nextId);
        Log.d("DynamoDBHelper", "Inserting new note with ID: " + nextId);
        dynamoDBMapper.save(note);
    }

    // Get all notes for a user
    public List<Note> getAllNotes(String userId) {
        Note noteKey = new Note();
        noteKey.setUserId(userId);

        DynamoDBQueryExpression<Note> queryExpression = new DynamoDBQueryExpression<Note>()
                .withHashKeyValues(noteKey)
                .withConsistentRead(false);  // Using eventually consistent reads for better performance

        List<Note> notes = dynamoDBMapper.query(Note.class, queryExpression);
        Log.d("DynamoDBHelper", "Retrieved " + notes.size() + " notes for user: " + userId);
        return notes;
    }

    // Update existing note
    public void updateNote(Note note) {
        if (note.getUserId() == null) {
            throw new IllegalArgumentException("UserID cannot be null for update");
        }
        Log.d("DynamoDBHelper", "Updating note with ID: " + note.getId());
        dynamoDBMapper.save(note);
    }

    // Delete note
    public void deleteNote(Note note) {
        /*Log.d("DynamoDBHelper", "Deleting note with ID: " + note.getId());
        dynamoDBMapper.delete(note);*/

        if (note == null) {
            throw new IllegalArgumentException("Cannot delete null note");
        }
        Log.d("DynamoDBHelper", "Deleting note with ID: " + note.getId());
        dynamoDBMapper.delete(note);
        Log.d("DynamoDBHelper", "Note deleted successfully");
    }

    // Get note by ID
    public Note getNoteById(String userId, int noteId) {
        return dynamoDBMapper.load(Note.class, userId, noteId);
    }

    // Method to get the next available ID
    public int getNextId(String userId) {
        try {
            Note noteKey = new Note();
            noteKey.setUserId(userId);

            DynamoDBQueryExpression<Note> queryExpression = new DynamoDBQueryExpression<Note>()
                    .withHashKeyValues(noteKey)
                    .withScanIndexForward(false)  // Sort in descending order
                    .withLimit(1);                // Get only the highest ID

            List<Note> result = dynamoDBMapper.query(Note.class, queryExpression);

            if (result.isEmpty()) {
                Log.d("DynamoDBHelper", "No existing notes found, starting with ID 1");
                return 1;  // Start with ID 1 if no notes exist
            } else {
                int nextId = result.get(0).getId() + 1;
                Log.d("DynamoDBHelper", "Next available ID: " + nextId);
                return nextId;  // Increment the highest ID
            }
        } catch (Exception e) {
            Log.e("DynamoDBHelper", "Error getting next ID: " + e.getMessage(), e);
            // If there's an error, return a safe default
            return (int) System.currentTimeMillis() % 100000; // Use current time as fallback
        }
    }

    // get pagination

    // Add this method to DynamoDBHelper.java
    public List<Note> getPaginatedNotes(String userId, int offset, int limit) {
        try {
            Note noteKey = new Note();
            noteKey.setUserId(userId);

            DynamoDBQueryExpression<Note> queryExpression = new DynamoDBQueryExpression<Note>()
                    .withHashKeyValues(noteKey)
                    .withConsistentRead(false);  // Using eventually consistent reads for better performance

            List<Note> notes = dynamoDBMapper.query(Note.class, queryExpression);
            Log.d("DynamoDBHelper", "Retrieved total notes: " + notes.size() + " for user: " + userId);

            // Apply pagination
            int fromIndex = Math.min(offset, notes.size());
            int toIndex = Math.min(fromIndex + limit, notes.size());

            return fromIndex < toIndex ? notes.subList(fromIndex, toIndex) : new ArrayList<>();

        } catch (Exception e) {
            Log.e("DynamoDBHelper", "Error in getPaginatedNotes: " + e.getMessage(), e);
            return new ArrayList<>();
        }
    }

}