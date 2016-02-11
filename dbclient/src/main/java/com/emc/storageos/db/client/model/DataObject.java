/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.util.KeyspaceUtil;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.model.valid.Length;

/**
 * Base data object
 */
public abstract class DataObject implements Serializable {
    static final long serialVersionUID = -5278839624050514418L;
    public static final String INACTIVE_FIELD_NAME = "inactive";
    private static final int DEFAULT_MIN_LABEL_LENGTH = 2;
    private static final String READ_LABEL_METHOD_NAME = "getLabel";

    private static final Logger _log = LoggerFactory.getLogger(DataObject.class);

    // urn:<zone id>:<record uuid>
    protected URI _id;

    // user label
    private String _label = "";

    // delete marker
    protected Boolean _inactive;

    // status for operations in flight - updates to this field
    // lives for 6 hours
    protected transient OpStatusMap _status;

    // track - set of fields modified
    protected transient Set<String> _changed;

    // track - set of fields set
    protected transient Set<String> _initialized = new HashSet<>();

    // tags
    private transient ScopedLabelSet _tags;

    // creation time
    private Calendar _creationTime;

    // A bitwise OR of supported internal flags that can be used
    // to control or restrict specific behaviors relative to the
    // data object.
    private Long _internalFlags = new Long(0);

    private Boolean _global;

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();

        out.writeObject(_id);
        out.writeObject(_label);
        if (_inactive == null) {
            out.writeBoolean(false);
        } else {
            out.writeBoolean(_inactive);
        }
        out.writeObject(_creationTime);
        out.writeLong(_internalFlags);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();

        _id = (URI) in.readObject();
        _label = (String) in.readObject();
        _inactive = in.readBoolean();
        _creationTime = (Calendar) in.readObject();
        _internalFlags = in.readLong();
        _initialized = new HashSet<>();
    }

    /**
     * get identifier
     * 
     * @return
     */
    @XmlElement
    @Id
    public URI getId() {
        return _id;
    }

    /**
     * set identifier
     * 
     * @param id
     */
    public void setId(URI id) {
        _id = id;
        _global = (KeyspaceUtil.isGlobal(this.getClass()));
    }

    /**
     * get label
     * 
     * @return
     */
    @XmlElement(name = "name")
    @PrefixIndex(cf = "LabelPrefixIndex")
    @Name("label")
    @Length(min = 2, max = 30)
    public String getLabel() {
        return _label;
    }

    /**
     * set label
     * 
     * @param label
     */
    public void setLabel(String label) {
        // COP-18886 revert this fix for Darth SP1 to unblock unmanaged volume ingestion
        // it didn't really help us much if we don't fix existing records anyways.
        // validateLabel(label);
        _label = label;
        setChanged("label");
    }
    
    private void validateLabel(String label) {
        int minLength = getPrefixIndexMinLength();
        if (label != null && label.length() < minLength) {
            String clazzName = this.getClass().getSimpleName();
            throw DatabaseException.fatals.fieldLengthTooShort(clazzName, this.getId(), READ_LABEL_METHOD_NAME, label.length(), minLength);
        }
    }
    
    private int getPrefixIndexMinLength()  {
        int length = DEFAULT_MIN_LABEL_LENGTH;
        try {
            Method method = DataObject.class.getDeclaredMethod(READ_LABEL_METHOD_NAME, null);
            PrefixIndex annotation = method.getAnnotation(PrefixIndex.class);
            length = annotation.minChars();
        } catch (Exception e) {
            _log.error("get declared method error:", e);
        }
        return length;
    }

    /**
     * get inactive
     * 
     * @return
     */
    @DecommissionedIndex("Decommissioned")
    @XmlElement
    @Name("inactive")
    public Boolean getInactive() {
        return (_inactive != null) && _inactive;
    }

    /**
     * set inactive
     * 
     * @param inactive
     */
    public void setInactive(Boolean inactive) {
        _inactive = inactive;
        setChanged("inactive");
    }

    /**
     * Get status
     * 
     * @return
     */
    @XmlElementWrapper(name = "operationStatus")
    @Ttl(60 * 60 * 6)
    @Name("status")
    @ClockIndependent(Operation.class)
    public OpStatusMap getOpStatus() {
        if (_status == null) {
            _status = new OpStatusMap();
        }
        return _status;
    }

    /**
     * Set status map - overwrites the existing map
     * 
     * @param map StringMap to set
     */
    public void setOpStatus(OpStatusMap map) {
        _status = map;
    }

    @Name("tags")
    @XmlElementWrapper(name = "tags")
    @ScopedLabelIndex(cf = "ScopedTagPrefixIndex")
    public ScopedLabelSet getTag() {
        return _tags;
    }

    /**
     * Tag settter
     * 
     * @param tags
     */
    public void setTag(ScopedLabelSet tags) {
        _tags = tags;
    }

    @Name("creationTime")
    public Calendar getCreationTime() {
        return _creationTime;
    }

    public void setCreationTime(Calendar creationTime) {
        _creationTime = creationTime;
        setChanged("creationTime");
    }

    /**
     * This accessor is deprecated in favor of the safer
     * enum-based versions and should generally only be
     * used by the db serialization logic
     */
    @Name("internalFlags")
    @Deprecated
    public Long getInternalFlags() {
        return _internalFlags;
    }

    /**
     * This accessor is deprecated in favor of the safer
     * enum-based versions and should generally only be
     * used by the db serialization logic
     */
    @Deprecated
    public void setInternalFlags(Long flags) {
        if (flags == null) {
            flags = new Long(0);
        }
        // make sure we don't trigger a column update if the flag values aren't actually changed
        if (!flags.equals(_internalFlags)) {
            _internalFlags = flags;
            setChanged("internalFlags");
        }
    }

    /**
     * Returns true if the provided flag is set
     * 
     * @param flag the flag to test
     * @return true if set
     */
    public boolean checkInternalFlags(Flag flag) {
        if (flag == null) {
            _log.warn("checkInternalFlags called with null argument");
            return false;
        }
        return (_internalFlags & flag.getMask()) != 0;
    }

    /**
     * Clear all supplied bit flags
     */
    public void clearInternalFlags(Flag... flags) {
        if (flags == null) {
            _log.warn("clearInternalFlags called with null argument");
            return;
        }
        long removeMask = 0;
        for (Flag flag : flags) {
            removeMask |= flag.getMask();
        }
        setInternalFlags(_internalFlags & ~removeMask);
    }

    /**
     * Set all supplied bit flags
     */
    public void addInternalFlags(Flag... flags) {
        if (flags == null) {
            _log.warn("addInternalFlags called with null argument");
            return;
        }
        long addMask = 0;
        for (Flag flag : flags) {
            addMask |= flag.getMask();
        }
        setInternalFlags(_internalFlags | addMask);
    }

    /**
     * Mark a field as modified
     * 
     * @param field name of the field modified
     */
    protected void setChanged(String field) {
        if (_changed != null) {
            _changed.add(field);
        }
        _initialized.add(field);
    }

    /**
     * Checks if the field with the given name is marked as changed
     * 
     * @param field name of the field to check
     * @return true if modified, false otherwise
     */
    public boolean isChanged(String field) {
        return (_changed != null && _changed.contains(field));
    }

    /**
     * clears changed flag for the given name
     * 
     * @param field name of the field to check
     */
    public void clearChangedValue(String field) {
        if (_changed != null) {
            _changed.remove(field);
        }
    }

    /**
     * mark changed flag for the given name
     * 
     * @param field name of the field to check
     */
    public void markChangedValue(String field) {
        setChanged(field);
    }

    /**
     * Checks if the field the given name was instanciated from the DB
     * 
     * @param field name of the field to check
     * @return true if modified, false otherwise
     */
    public boolean isInitialized(String field) {
        return (_initialized.contains(field));
    }

    /**
     * Start tracking changes for this object
     */
    public void trackChanges() {
        _changed = new HashSet<String>();
    }

    /**
     * This method will be called to check if this object is safe for deletion
     * overload this method in the derived class if there is anything specific to
     * check for on the object before deletion
     * 
     * @return null if no active references, otherwise, detail type of the depedency returned
     */
    public String canBeDeleted() {
        return null;
    }

    protected DataObject() {
        trackChanges();
    }

    /**
     * Static method to create an instance of an object with the specified id
     * used from db deserialize to instantiate objects
     * 
     * @param clazz DataObject class to create
     * @param id URI of the object
     * @param <T> DataObject type
     * @return Object created
     * @throws InstantiationException
     * @throws IllegalAccessException
     */
    public static <T extends DataObject> DataObject createInstance(Class<T> clazz, URI id)
            throws InstantiationException, IllegalAccessException {
        T created = clazz.newInstance();
        created.setId(id);
        created._changed = null;
        return created;
    }

    @Name("global")
    public Boolean isGlobal() {
        return (_global != null) && _global;
    }

    /**
     * Bit flags that can be set on the data object to control or restrict
     * behavior relative to the data object.
     * 
     * We don't yet have the ability to serialize something like an EnumSet into
     * the database, so we'll make do with defining the bits as Enum's and then
     * providing some typesafe setters/getters.
     */
    public static enum Flag {
        INTERNAL_OBJECT(0),         // 0x01
        NO_METERING(1),             // 0x02
        NO_PUBLIC_ACCESS(2),        // 0x04
        SUPPORTS_FORCE(3),          // 0x08
        RECOVERPOINT(4),            // 0x10
        DELETION_IN_PROGRESS(5),    // 0x20
        RECOVERPOINT_JOURNAL(6);    // 0x40

        private final long mask;

        /**
         * Construct an enum, using an explicit bit position rather than just using the ordinal to protect
         * against future add/remove of flags
         * 
         * @param bitPosition the bit position to represent this instance
         */
        private Flag(int bitPosition) {
            if (bitPosition < 0 || bitPosition > 63) {
                throw new IllegalArgumentException(bitPosition + " is out of bounds for a long bit mask");
            }
            this.mask = 1 << bitPosition;
        }

        public long getMask() {
            return mask;
        }
    }

    public void filterOutNulls(Collection<? extends DataObject> args) {
        if (args != null) {
            Iterator<? extends DataObject> iterator = args.iterator();
            while (iterator.hasNext()) {
                DataObject dataObject = iterator.next();
                if (dataObject == null) {
                    iterator.remove();
                }
            }
        }
    }

    public boolean checkForNull(DataObject... args) {
        boolean isNullFound = false;
        if (args != null) {
            for (DataObject dataObject : args) {
                if (dataObject == null) {
                    isNullFound = true;
                    break;
                }
            }
        }
        return isNullFound;
    }

    public String forDisplay() {
        if (_label != null && !_label.isEmpty()) {
            return String.format("%s (%s)", _label, _id);
        } else {
            return _id.toString();
        }
    }
}
