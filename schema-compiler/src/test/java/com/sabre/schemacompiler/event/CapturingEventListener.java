/*
 * Copyright (c) 2011, Sabre Inc.
 */
package com.sabre.schemacompiler.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Model event listener that captures events for later analysis and assertions by test cases.
 * 
 * @author S. Livezey
 */
public class CapturingEventListener<E extends ModelEvent<S>, S> implements ModelEventListener<E, S> {
	
	private List<E> capturedEvents = new ArrayList<E>();
	private Class<E> eventClass;
	private Class<S> sourceObjectClass;
	
	/**
	 * Constructor that specifies the run-time types used to identify the event and source
	 * object types to which the listener should respond.
	 * 
	 * @param eventClass  the type of the events to be processed by this listener
	 * @param sourceObjectClass  the type of source objects to be processed by this listener
	 */
	@SuppressWarnings("unchecked")
	public CapturingEventListener(Class<?> eventClass, Class<?> sourceObjectClass) {
		this.eventClass = (Class<E>) eventClass;
		this.sourceObjectClass = (Class<S>) sourceObjectClass;
	}
	
	/**
	 * @see com.sabre.schemacompiler.event.ModelEventListener#processModelEvent(com.sabre.schemacompiler.event.ModelEvent)
	 */
	@Override
	public void processModelEvent(E event) {
		capturedEvents.add((E) event);
	}
	
	/**
	 * Returns the events that have been captured by this listener.
	 *
	 * @return List<E>
	 */
	public List<E> getCapturedEvents() {
		return Collections.unmodifiableList(capturedEvents);
	}

	/**
	 * @see com.sabre.schemacompiler.event.ModelEventListener#getEventClass()
	 */
	@Override
	public Class<E> getEventClass() {
		return eventClass;
	}

	/**
	 * @see com.sabre.schemacompiler.event.ModelEventListener#getSourceObjectClass()
	 */
	@Override
	public Class<S> getSourceObjectClass() {
		return sourceObjectClass;
	}
	
}