package com.cloudnotes.app.adapters;

import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cloudnotes.app.R;
import com.cloudnotes.app.entities.Note;
import com.cloudnotes.app.listeners.NotesListener;
import com.cloudnotes.app.utility.NoteTags;
import com.bumptech.glide.Glide;
import com.makeramen.roundedimageview.RoundedImageView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class NotesAdapter extends RecyclerView.Adapter<NotesAdapter.NoteViewHolder> {

    private List<Note> notes;
    private final NotesListener notesListener;
    private Timer timer;
    private List<Note> notesSource;
    private String activeTagFilter = NoteTags.ALL;
    private String activeSearchQuery = "";


    public NotesAdapter(List<Note> notes, NotesListener notesListener) {
        this.notes = notes;
        this.notesListener = notesListener;
        this.notesSource = new ArrayList<>(notes);
    }

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new NoteViewHolder(
                LayoutInflater.from(parent.getContext()).inflate(R.layout.item_container_note, parent, false)
        );
    }

    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        try {
            if (position >= notes.size()) {
                return;
            }

            Note note = notes.get(position);
            if (note == null) {
                return;
            }

            holder.setNote(note);

            holder.itemView.setOnClickListener(v -> {
                int currentPosition = holder.getAdapterPosition();
                if (currentPosition != RecyclerView.NO_POSITION && notesListener != null) {
                    notesListener.onNoteCLicked(notes.get(currentPosition), currentPosition);
                }
            });

        } catch (Exception e) {
            Log.e("NotesAdapter", "Error binding view: " + e.getMessage());
        }
    }

    @Override
    public void onViewRecycled(@NonNull NoteViewHolder holder) {
        super.onViewRecycled(holder);
        holder.layoutNote.setOnClickListener(null);
        holder.itemView.setOnClickListener(null);
    }

    @Override
    public int getItemCount() {
        return notes.size();
    }

    @Override
    public int getItemViewType(int position) {
        return position;
    }

    static class NoteViewHolder extends RecyclerView.ViewHolder {

        TextView textTitle, textSubtitle, textDateTime, textTag;
        View viewColorAccent;
        LinearLayout layoutNote;
        RoundedImageView imageNote;

        NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            textTitle = itemView.findViewById(R.id.textTitle);
            textSubtitle = itemView.findViewById(R.id.textSubtitle);
            textDateTime = itemView.findViewById(R.id.textDateTime);
            textTag = itemView.findViewById(R.id.textTag);
            viewColorAccent = itemView.findViewById(R.id.viewColorAccent);
            layoutNote = itemView.findViewById(R.id.layoutNote);
            imageNote = itemView.findViewById(R.id.imageNote);
        }

        void setNote(Note note) {
            textTitle.setText(note.getTitle());
            if (note.getSubtitle() == null || note.getSubtitle().trim().isEmpty()) {
                textSubtitle.setVisibility(View.GONE);
            } else {
                textSubtitle.setVisibility(View.VISIBLE);
                textSubtitle.setText(note.getSubtitle());
            }

            if (note.getTags() != null && !note.getTags().trim().isEmpty()) {
                textTag.setVisibility(View.VISIBLE);
                textTag.setText("#" + note.getTags());
            } else {
                textTag.setVisibility(View.GONE);
            }

            textDateTime.setText(note.getDateTime());

            if (viewColorAccent != null) {
                String noteColor = note.getColor() != null ? note.getColor() : "#7C5CFF";
                viewColorAccent.setBackgroundColor(Color.parseColor(noteColor));
            }

            if (note.getImagePath() != null && !note.getImagePath().trim().isEmpty()) {
                if (note.getImagePath().startsWith("http")) {
                    Glide.with(imageNote.getContext())
                            .load(note.getImagePath())
                            .placeholder(R.drawable.demo_profile)
                            .error(R.drawable.demo_profile_3)
                            .into(imageNote);
                    imageNote.setVisibility(View.VISIBLE);
                } else {
                    Glide.with(imageNote.getContext())
                            .load(new File(note.getImagePath()))
                            .placeholder(R.drawable.demo_profile)
                            .error(R.drawable.demo_profile_3)
                            .into(imageNote);
                    imageNote.setVisibility(View.VISIBLE);
                }
            } else {
                imageNote.setVisibility(View.GONE);
            }
        }
    }

    public void searchNotes(final String searchKeyword) {
        if (timer != null) {
            timer.cancel();
        }

        if (notesSource == null) {
            notesSource = new ArrayList<>(notes);
        }

        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                activeSearchQuery = searchKeyword == null ? "" : searchKeyword.trim();
                new Handler(Looper.getMainLooper()).post(() -> applyFilters());
            }
        }, 300);
    }

    public void filterByTag(String tag) {
        activeTagFilter = tag == null || tag.isEmpty() ? NoteTags.ALL : tag;
        applyFilters();
    }

    private void applyFilters() {
        if (notesSource == null) {
            return;
        }

        List<Note> filteredNotes = new ArrayList<>();
        String query = activeSearchQuery.toLowerCase();

        for (Note note : notesSource) {
            if (note == null) {
                continue;
            }
            if (!matchesTagFilter(note)) {
                continue;
            }
            if (!query.isEmpty()) {
                boolean matchesSearch =
                        (note.getTitle() != null && note.getTitle().toLowerCase().contains(query)) ||
                                (note.getSubtitle() != null && note.getSubtitle().toLowerCase().contains(query)) ||
                                (note.getNoteText() != null && note.getNoteText().toLowerCase().contains(query)) ||
                                (note.getTags() != null && note.getTags().toLowerCase().contains(query));
                if (!matchesSearch) {
                    continue;
                }
            }
            filteredNotes.add(note);
        }

        notes.clear();
        notes.addAll(filteredNotes);
        notifyDataSetChanged();
    }

    private boolean matchesTagFilter(Note note) {
        if (NoteTags.ALL.equals(activeTagFilter)) {
            return true;
        }
        String noteTag = note.getTags();
        return noteTag != null && activeTagFilter.equalsIgnoreCase(noteTag.trim());
    }

    public void cancelTimer() {
        if (timer != null) {
            timer.cancel();
        }
    }

    public void updateSourceData(List<Note> newNotes) {
        if (newNotes != null) {
            notesSource = new ArrayList<>(newNotes);
            applyFilters();
        }
    }

    public void refreshSourceList() {
        activeSearchQuery = "";
        applyFilters();
    }

    public void updateNotes(List<Note> newNotes) {
        this.notesSource = newNotes != null ? new ArrayList<>(newNotes) : new ArrayList<>();
        applyFilters();
    }
}
