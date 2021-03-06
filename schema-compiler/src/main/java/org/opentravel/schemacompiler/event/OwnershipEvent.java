/**
 * Copyright (C) 2014 OpenTravel Alliance (info@opentravel.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opentravel.schemacompiler.event;

/**
 * Event used to broadcast events associated with addition or removal of a child item to/from an
 * owning model entity.
 * 
 * @param <S>
 *            the source object type for the event
 * @param <I>
 *            the type of item that was added or removed from the parent entity
 * @author S. Livezey
 */
public class OwnershipEvent<S, I> extends ModelEvent<S> {

    private I affectedItem;

    /**
     * Constructor that specifies the event type and source object for the event.
     * 
     * @param type
     *            the type of the event being broadcast
     * @param source
     *            the source object that was modified to create the event
     */
    public OwnershipEvent(ModelEventType type, S source) {
        super(type, source);
    }

    /**
     * Constructor that specifies the event type and source object for the event.
     * 
     * @param type
     *            the type of the event being broadcast
     * @param source
     *            the source object that was modified to create the event
     * @param affectedItem
     *            the affected item instance to assign
     */
    public OwnershipEvent(ModelEventType type, S source, I affectedItem) {
        this(type, source);
        setAffectedItem(affectedItem);
    }

    /**
     * Returns the item that was added or deleted by this event.
     * 
     * @return I
     */
    public I getAffectedItem() {
        return affectedItem;
    }

    /**
     * Assigns the item that was added or deleted by this event.
     * 
     * @param affectedItem
     *            the affected item instance to assign
     */
    protected void setAffectedItem(I affectedItem) {
        if (affectedItem == null) {
            throw new IllegalArgumentException(
                    "The affected item for an ownership event cannot be null.");
        }
        this.affectedItem = affectedItem;
    }

}
