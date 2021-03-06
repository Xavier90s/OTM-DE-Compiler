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
 * Base class for event filters that can limit the number and/or type of messages that are forwarded
 * to an underlying listener.
 * 
 * @param <E>
 *            the event type that the underlying listener is designed to process
 * @param <S>
 *            the source object type for the events to be processed by the underlying listener
 * @author S. Livezey
 */
public abstract class ModelEventFilter<E extends ModelEvent<S>, S> implements
        ModelEventListener<E, S> {

    private ModelEventListener<E, S> listener;

    /**
     * Constructor that assignes the underlying listener for this filter.
     * 
     * @param listener
     *            the listener instance to assign (required - cannot be null)
     */
    public ModelEventFilter(ModelEventListener<E, S> listener) {
        if (listener == null) {
            throw new NullPointerException("The model listener instance cannot be null.");
        }
        this.listener = listener;
    }

    /**
     * @see org.opentravel.schemacompiler.event.ModelEventListener#processModelEvent(org.opentravel.schemacompiler.event.ModelEvent)
     */
    @Override
    public void processModelEvent(E event) {
        if (isAllowableEvent(event)) {
            listener.processModelEvent(event);
        }
    }

    /**
     * Returns true if the event is considered allowable by this filter and should be forwarded to
     * the underlying listener.
     * 
     * @param event
     *            the event instance to evaluate
     * @return boolean
     */
    protected abstract boolean isAllowableEvent(ModelEvent<S> event);

    /**
     * @see org.opentravel.schemacompiler.event.ModelEventListener#getEventClass()
     */
    @Override
    public Class<?> getEventClass() {
        return listener.getEventClass();
    }

    /**
     * @see org.opentravel.schemacompiler.event.ModelEventListener#getSourceObjectClass()
     */
    @Override
    public Class<S> getSourceObjectClass() {
        return listener.getSourceObjectClass();
    }

}
