package com.cloudnotes.app.utility;

import com.cloudnotes.app.R;

import java.util.Arrays;
import java.util.List;

public final class NoteTags {
    public static final String ALL = "all";
    public static final String WORK = "work";
    public static final String STUDY = "study";
    public static final String PERSONAL = "personal";
    public static final String IDEAS = "ideas";

    private NoteTags() {
    }

    public static List<String> filterTags() {
        return Arrays.asList(ALL, WORK, STUDY, PERSONAL, IDEAS);
    }

    public static List<String> assignableTags() {
        return Arrays.asList(WORK, STUDY, PERSONAL, IDEAS);
    }

    public static int getLabelRes(String tag) {
        switch (tag) {
            case WORK:
                return R.string.tag_work;
            case STUDY:
                return R.string.tag_study;
            case PERSONAL:
                return R.string.tag_personal;
            case IDEAS:
                return R.string.tag_ideas;
            case ALL:
            default:
                return R.string.tag_all;
        }
    }
}
