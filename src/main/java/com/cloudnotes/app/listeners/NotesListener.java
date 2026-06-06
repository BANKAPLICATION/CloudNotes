package com.cloudnotes.app.listeners;

import com.cloudnotes.app.entities.Note;

public interface NotesListener {
    void onNoteCLicked(Note note, int position);
}
