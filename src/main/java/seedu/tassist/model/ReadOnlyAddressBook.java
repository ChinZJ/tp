package seedu.tassist.model;

import javafx.collections.ObservableList;
import seedu.tassist.model.person.Person;

/**
 * Unmodifiable view of an address book.
 */
public interface ReadOnlyAddressBook {

    /**
     * Returns an unmodifiable view of the persons list.
     * This list will not contain any duplicate persons.
     */
    ObservableList<Person> getPersonList();

}
