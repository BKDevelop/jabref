package net.sf.jabref.model.groups;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.sf.jabref.model.FieldChange;
import net.sf.jabref.model.entry.BibEntry;
import net.sf.jabref.model.entry.KeywordList;
import net.sf.jabref.model.strings.StringUtil;

/**
 * Matches entries if a given field contains a specified word.
 */
public class SimpleKeywordGroup extends KeywordGroup implements GroupEntryChanger {

    protected final Character keywordSeparator;
    private final List<String> searchWords;

    public SimpleKeywordGroup(String name, GroupHierarchyType context, String searchField,
                              String searchExpression, boolean caseSensitive, Character keywordSeparator) {
        super(name, context, searchField, searchExpression, caseSensitive);

        this.keywordSeparator = keywordSeparator;
        this.searchWords = StringUtil.getStringAsWords(searchExpression);
    }

    private static boolean containsCaseInsensitive(Set<String> searchIn, List<String> searchFor) {
        for (String searchWord : searchFor) {
            if (!containsCaseInsensitive(searchIn, searchWord)) {
                return false;
            }
        }
        return true;
    }

    private static boolean containsCaseInsensitive(Set<String> searchIn, String searchFor) {
        for (String word : searchIn) {
            if (word.equalsIgnoreCase(searchFor)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<FieldChange> add(List<BibEntry> entriesToAdd) {
        Objects.requireNonNull(entriesToAdd);

        List<FieldChange> changes = new ArrayList<>();
        for (BibEntry entry : entriesToAdd) {
            if (!contains(entry)) {
                String oldContent = entry.getField(searchField).orElse("");
                KeywordList wordlist = KeywordList.parse(oldContent, keywordSeparator);
                wordlist.add(searchExpression);
                String newContent = wordlist.getAsString(keywordSeparator);
                entry.setField(searchField, newContent).ifPresent(changes::add);
            }
        }
        return changes;
    }

    @Override
    public List<FieldChange> remove(List<BibEntry> entriesToRemove) {
        Objects.requireNonNull(entriesToRemove);
        List<FieldChange> changes = new ArrayList<>();
        for (BibEntry entry : entriesToRemove) {
            if (contains(entry)) {
                String oldContent = entry.getField(searchField).orElse("");
                KeywordList wordlist = KeywordList.parse(oldContent, keywordSeparator);
                wordlist.remove(searchExpression);
                String newContent = wordlist.getAsString(keywordSeparator);
                entry.setField(searchField, newContent).ifPresent(changes::add);
            }
        }
        return changes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SimpleKeywordGroup)) {
            return false;
        }
        SimpleKeywordGroup other = (SimpleKeywordGroup) o;
        return getName().equals(other.getName())
                && (getHierarchicalContext() == other.getHierarchicalContext())
                && searchField.equals(other.searchField)
                && searchExpression.equals(other.searchExpression)
                && (caseSensitive == other.caseSensitive)
                && keywordSeparator == other.keywordSeparator;
    }

    @Override
    public boolean contains(BibEntry entry) {
        Set<String> content = getFieldContentAsWords(entry);
        if (caseSensitive) {
            return content.containsAll(searchWords);
        } else {
            return containsCaseInsensitive(content, searchWords);
        }
    }

    private Set<String> getFieldContentAsWords(BibEntry entry) {
        return entry.getFieldAsWords(searchField);
    }

    @Override
    public AbstractGroup deepCopy() {
        return new SimpleKeywordGroup(getName(), getHierarchicalContext(), searchField, searchExpression,
                caseSensitive, keywordSeparator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getName(),
                getHierarchicalContext(),
                searchField,
                searchExpression,
                caseSensitive,
                keywordSeparator);
    }
}
