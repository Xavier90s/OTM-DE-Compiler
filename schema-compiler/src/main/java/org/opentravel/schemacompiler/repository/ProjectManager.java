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
package org.opentravel.schemacompiler.repository;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.JAXBElement;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.opentravel.ns.ota2.project_v01_00.ManagedProjectItemType;
import org.opentravel.ns.ota2.project_v01_00.ProjectItemType;
import org.opentravel.ns.ota2.project_v01_00.ProjectType;
import org.opentravel.ns.ota2.project_v01_00.RepositoryRefType;
import org.opentravel.ns.ota2.project_v01_00.UnmanagedProjectItemType;
import org.opentravel.ns.ota2.repositoryinfo_v01_00.RepositoryPermission;
import org.opentravel.schemacompiler.codegen.CodeGenerationFilter;
import org.opentravel.schemacompiler.codegen.impl.DependencyFilterBuilder;
import org.opentravel.schemacompiler.event.ModelEventListener;
import org.opentravel.schemacompiler.event.ModelEventType;
import org.opentravel.schemacompiler.event.OwnershipEvent;
import org.opentravel.schemacompiler.ic.ImportManagementIntegrityChecker;
import org.opentravel.schemacompiler.loader.LibraryLoaderException;
import org.opentravel.schemacompiler.loader.LibraryModelLoader;
import org.opentravel.schemacompiler.loader.LibraryNamespaceResolver;
import org.opentravel.schemacompiler.loader.LoaderValidationMessageKeys;
import org.opentravel.schemacompiler.loader.impl.DefaultLibraryNamespaceResolver;
import org.opentravel.schemacompiler.loader.impl.FileValidationSource;
import org.opentravel.schemacompiler.loader.impl.LibraryStreamInputSource;
import org.opentravel.schemacompiler.loader.impl.LibraryValidationSource;
import org.opentravel.schemacompiler.model.AbstractLibrary;
import org.opentravel.schemacompiler.model.BuiltInLibrary;
import org.opentravel.schemacompiler.model.TLInclude;
import org.opentravel.schemacompiler.model.TLLibrary;
import org.opentravel.schemacompiler.model.TLLibraryStatus;
import org.opentravel.schemacompiler.model.TLModel;
import org.opentravel.schemacompiler.model.TLNamespaceImport;
import org.opentravel.schemacompiler.repository.impl.BuiltInProject;
import org.opentravel.schemacompiler.repository.impl.ProjectFileUtils;
import org.opentravel.schemacompiler.repository.impl.ProjectItemDependencyNavigator;
import org.opentravel.schemacompiler.repository.impl.ProjectItemImpl;
import org.opentravel.schemacompiler.saver.LibraryModelSaver;
import org.opentravel.schemacompiler.saver.LibrarySaveException;
import org.opentravel.schemacompiler.util.ExceptionUtils;
import org.opentravel.schemacompiler.util.URLUtils;
import org.opentravel.schemacompiler.validate.FindingType;
import org.opentravel.schemacompiler.validate.ValidationFindings;
import org.opentravel.schemacompiler.validate.ValidatorFactory;
import org.opentravel.schemacompiler.validate.impl.TLModelValidator;
import org.opentravel.schemacompiler.version.MajorVersionHelper;
import org.opentravel.schemacompiler.version.VersionScheme;
import org.opentravel.schemacompiler.version.VersionSchemeException;
import org.opentravel.schemacompiler.version.VersionSchemeFactory;

/**
 * Maintains the list of all project and project-items, and orchestrates the actions required to
 * manages changes.
 * 
 * @author S. Livezey
 */
public final class ProjectManager {

    private static Log log = LogFactory.getLog(ProjectManager.class);

    private static Map<TLModel, ProjectManager> instanceMap = new HashMap<TLModel, ProjectManager>();

    private RepositoryManager repositoryManager;
    private List<Project> projects = new ArrayList<Project>();
    private List<ProjectItem> projectItems = new ArrayList<ProjectItem>();
    private boolean autoSaveProjects;
    private Project builtInProject;
    private TLModel model;

    /**
     * Default constructor.
     */
    public ProjectManager() {
        this(new TLModel(), true, null);
    }

    /**
     * Constructs a new project manager using the specified auto-save option.
     * 
     * @param autoSaveProjects
     *            flag indicating whether member projects should be auto-saved
     */
    public ProjectManager(boolean autoSaveProjects) {
        this(new TLModel(), autoSaveProjects, null);
    }

    /**
     * Constructs a new project manager using the model instance provided.
     * 
     * @param model
     *            the model instance to assigne for the new manager
     */
    public ProjectManager(TLModel model) {
        this(model, true, null);
    }

    /**
     * Constructs a new project manager using the model instance provided.
     * 
     * @param model
     *            the model instance to assigne for the new manager
     * @param autoSaveProjects
     *            flag indicating whether member projects should be auto-saved
     * @param repositoryManager
     *            the repository manager to use when interfacing with remote repositories
     */
    public ProjectManager(TLModel model, boolean autoSaveProjects,
            RepositoryManager repositoryManager) {
        try {
            if (model == null) {
                throw new NullPointerException("The underlying model for a project cannot be null.");
            }
            this.model = model;
            this.model.addListener(new IncludeDependencyListener());
            this.model.addListener(new ImportDependencyListener());
            this.projects.add(builtInProject = new BuiltInProject(this));
            this.repositoryManager = (repositoryManager == null) ? RepositoryManager.getDefault()
                    : repositoryManager;
            this.autoSaveProjects = autoSaveProjects;

            instanceMap.put(model, this); // If a project manager was already associated with this
                                          // model, this new instance will replace the old one.

        } catch (RepositoryException e) {
            log.error(
                    "Exception initializing local repository - only unmanaged project files are accessible.",
                    e);
        }
    }

    /**
     * Returns the <code>TLModel</code> instance shared among all of the existing projects.
     * 
     * @return TLModel
     */
    public TLModel getModel() {
        return model;
    }

    /**
     * If the given model is associated with a <code>ProjectManager</code> instance, that instance
     * will be returned. Otherwise, this method will return null.
     * 
     * @param model
     *            the model instance for which to return the associated project manager
     */
    public static ProjectManager getProjectManager(TLModel model) {
        return instanceMap.get(model);
    }

    /**
     * Returns the repository manager used to download and edit content from a remote OTA2.0
     * repository.
     * 
     * @return RepositoryManager
     */
    public RepositoryManager getRepositoryManager() {
        return repositoryManager;
    }

    /**
     * Returns the list of all registered projects.
     * 
     * @return List<Project>
     */
    public List<Project> getAllProjects() {
        return Collections.unmodifiableList(projects);
    }

    /**
     * Returns the flag value indicating whether projects should be automatically re-saved whenever
     * a change to the project's persistent state is detected. This includes loading operations in
     * which new libraries are automatically imported and/or included. If the auto-save flag is
     * false, clients are required to manually save any such state changes to affects project(s).
     * 
     * @return boolean (default is true)
     */
    public boolean getAutoSaveProjects() {
        return autoSaveProjects;
    }

    /**
     * Assigns the flag value indicating whether projects should be automatically re-saved whenever
     * a change to the project's persistent state is detected. This includes loading operations in
     * which new libraries are automatically imported and/or included. If the auto-save flag is
     * false, clients are required to manually save any such state changes to affects project(s).
     * 
     * @param autoSaveProjects
     *            the flag value to assign (default is true)
     */
    public void setAutoSaveProjects(boolean autoSaveProjects) {
        this.autoSaveProjects = autoSaveProjects;
    }

    /**
     * Constructs a new <code>Project</code> instance using the information provided and adds it to
     * the list of projects maintained by this project manager instance.
     * 
     * @param projectFile
     *            the file location where the project will be saved
     * @param projectId
     *            the ID of the project to create
     * @param projectName
     *            the user-displayable name of the project to create
     * @param description
     *            a description for the project to be created
     * @return Project
     * @throws LibrarySaveException
     *             thrown if the new project instance cannot be saved
     * @throws IllegalArgumentException
     *             thrown if the file and/or ID of the project are already in use
     */
    public Project newProject(File projectFile, String projectId, String projectName,
            String description) throws LibrarySaveException {
        Project project = new Project(this);

        project.setProjectFile(projectFile);
        project.setProjectId(projectId);
        project.setName(projectName);
        project.setDescription(description);

        validateProjectID(projectId, null);
        validateProjectFile(projectFile, null);

        ProjectFileUtils.saveProjectFile(project);
        projects.add(project);

        return project;
    }

    /**
     * Loads the specified project file and incorporates its content into the shared model instance.
     * 
     * @param projectFile
     *            the file location of the project to be loaded
     * @return Project
     * @throws LibraryLoaderException
     *             thrown if the contents of the project cannot be loaded
     * @throws IllegalArgumentException
     *             thrown if the file and/or ID of the project are already in use
     * @throws RepositoryException
     *             thrown if one or more managed repository items cannot be accessed
     */
    public Project loadProject(File projectFile) throws LibraryLoaderException, RepositoryException {
        return loadProject(projectFile, null);
    }

    /**
     * Loads the specified project file and incorporates its content into the shared model instance.
     * 
     * @param projectFile
     *            the file location of the project to be loaded
     * @param findings
     *            validation findings where errors/warnings from the loading operation will be
     *            reported
     * @return Project
     * @throws LibraryLoaderException
     *             thrown if the contents of the project cannot be loaded
     * @throws IllegalArgumentException
     *             thrown if the file and/or ID of the project are already in use
     * @throws RepositoryException
     *             thrown if one or more managed repository items cannot be accessed
     */
    public Project loadProject(File projectFile, ValidationFindings findings)
            throws LibraryLoaderException, RepositoryException {
        ProjectType jaxbProject = ProjectFileUtils.loadJaxbProjectFile(projectFile, findings);
        Project project = null;

        if (jaxbProject != null) {
            ValidationFindings loaderFindings = new ValidationFindings();

            // Attempt to register any new repositories that are defined in this project file
            registerUnknownRepositories(jaxbProject, projectFile, loaderFindings);

            // Construct the new project instance and add it to the current list of projects
            project = new Project(this);
            project.setProjectId(jaxbProject.getProjectId());
            project.setProjectFile(projectFile);
            project.setName(jaxbProject.getName());
            project.setDescription(jaxbProject.getDescription());
            project.setDefaultContextId(jaxbProject.getDefaultContextId());

            validateProjectID(project.getProjectId(), null);
            validateProjectFile(projectFile, null);
            projects.add(project);

            // Load the contents of the model using the library reference from each of the project
            // items
            // defined in the file.
            List<RepositoryItem> managedItems = new ArrayList<RepositoryItem>();
            List<File> unmanagedItemFiles = new ArrayList<File>();
            Map<String,String> repositoryLocations = new HashMap<>();
            RepositoryItem defaultItem = null;
            URL defaultItemUrl = null;

            for (JAXBElement<? extends ProjectItemType> jaxbItem : jaxbProject.getProjectItemBase()) {
                URL projectFolderUrl = URLUtils.toURL(projectFile.getParentFile());

                if (jaxbItem.getValue() instanceof UnmanagedProjectItemType) {
                    UnmanagedProjectItemType projectItem = (UnmanagedProjectItemType) jaxbItem
                            .getValue();
                    try {
                        URL libraryUrl = URLUtils.getResolvedURL(projectItem.getFileLocation(),
                                projectFolderUrl);
                        unmanagedItemFiles.add(URLUtils.toFile(libraryUrl));

                        if ((projectItem.isDefaultItem() != null) && projectItem.isDefaultItem()) {
                            defaultItemUrl = libraryUrl;
                        }

                    } catch (MalformedURLException e) {
                        loaderFindings.addFinding(FindingType.ERROR, new FileValidationSource(
                                projectFile),
                                LoaderValidationMessageKeys.ERROR_INVALID_PROJECT_ITEM_LOCATION,
                                projectItem.getFileLocation());
                    }

                } else if (jaxbItem.getValue() instanceof ManagedProjectItemType) {
                    ManagedProjectItemType projectItem = (ManagedProjectItemType) jaxbItem
                            .getValue();
                    try {
                        String repositoryId = projectItem.getRepository();
                        Repository repository;

                        if ((repositoryId != null) && (repositoryId.length() > 0)) {
                            repository = repositoryManager.getRepository(repositoryId);

                            if (repository == null) {
                                throw new RepositoryException("Unknown repository specified: "
                                        + repositoryId);
                            }
                        } else {
                            // If the item's repository ID was not explicitly specified, invoking
                            // the search on the
                            // repository manager will call will force a searchin all known
                            // repositories.
                            repository = repositoryManager;
                        }
                        RepositoryItem repositoryItem = repository.getRepositoryItem(
                                projectItem.getBaseNamespace(), projectItem.getFilename(),
                                projectItem.getVersion());

                        if (repositoryItem.getRepository() == null) {
                            loaderFindings.addFinding(FindingType.ERROR, new FileValidationSource(
                                    projectFile),
                                    LoaderValidationMessageKeys.ERROR_MISSING_REPOSITORY,
                                    projectItem.getFilename());
                        }
                        if ((projectItem.isDefaultItem() != null) && projectItem.isDefaultItem()) {
                            defaultItem = repositoryItem;
                        }
                        managedItems.add(repositoryItem);

                    } catch (RepositoryUnavailableException e) {
                        loaderFindings.addFinding(FindingType.ERROR, new FileValidationSource(
                                projectFile),
                                LoaderValidationMessageKeys.ERROR_REPOSITORY_UNAVAILABLE,
                                projectItem.getFilename(), projectItem.getRepository());

                    } catch (RepositoryException e) {
                        loaderFindings.addFinding(FindingType.ERROR, new FileValidationSource(
                                projectFile),
                                LoaderValidationMessageKeys.ERROR_LOADING_FROM_REPOSITORY,
                                projectItem.getFilename(), e.getClass().getSimpleName(), e
                                        .getMessage());
                    }
                }
            }

            // Attempt to load the project and its items into the current project manager session
            boolean success = false;
            try {
                loadAllProjectItems(unmanagedItemFiles, managedItems, project, loaderFindings);
                success = true;

            } finally {
                // If the load threw an exception, we need to discard the project and any items that
                // might have been loaded prior to the error
                if (!success) {
                    projects.remove(project);
                    purgeOrphanedProjectItems();
                }
            }

            // Assign the default project item
            if (defaultItemUrl != null) {
                AbstractLibrary defaultLib = model.getLibrary(defaultItemUrl);

                if (defaultLib != null) {
                    project.setDefaultItem(getProjectItem(defaultLib));
                }

            } else if (defaultItem != null) {
                for (ProjectItem item : projectItems) {
                    if ((item != null)
                            && ((item.getBaseNamespace() != null) && item.getBaseNamespace()
                                    .equals(defaultItem.getBaseNamespace()))
                            && ((item.getFilename() != null) && item.getFilename().equals(
                                    defaultItem.getFilename()))
                            && ((item.getVersion() != null) && item.getVersion().equals(
                                    defaultItem.getVersion()))) {
                        project.setDefaultItem(item);
                        break;
                    }
                }
            }

            // Validate for errors/warnings if requested by the caller
            if (findings != null) {
                findings.addAll(TLModelValidator.validateModel(model,
                        ValidatorFactory.COMPILE_RULE_SET_ID));
                findings.addAll(loaderFindings);
            }

        } else {
            throw new LibraryLoaderException("Unable to load project: " + projectFile.getName());
        }
        return project;
    }

    /**
     * Scans the contents of the given JAXB project object and attempts to register any repositories
     * that are not yet known to the local environment. If the local repository's meta-data already
     * contains a reference to a repository, it is left unchanged by this method. If a reference to
     * an as-yet unknown repository is called out, however, this method will attempt to register it
     * using the URL provided in the project file.
     * 
     * @param jaxbProject
     *            the raw JAXB content of the OTP project file
     * @param projectFile
     *            the file from which the JAXB project content was loaded
     * @param loaderFindings
     *            findings that will receive the warning if an invalid repository URL is encountered
     */
    private void registerUnknownRepositories(ProjectType jaxbProject, File projectFile,
            ValidationFindings loaderFindings) {
        Map<String, String> repositoryUrls = new HashMap<String, String>();
        Set<String> repositoryIds = new HashSet<String>();

        // Collect the IDs of the repositories that were explicitly referenced
        for (JAXBElement<? extends ProjectItemType> jaxbItem : jaxbProject.getProjectItemBase()) {
            if (jaxbItem.getValue() instanceof ManagedProjectItemType) {
                ManagedProjectItemType projectItem = (ManagedProjectItemType) jaxbItem.getValue();
                String repositoryId = projectItem.getRepository();

                if ((repositoryId != null) && (repositoryId.length() > 0)) {
                    repositoryIds.add(repositoryId);
                }
            }
        }

        // Build a map of known repository IDs and their associated URLs
        if (jaxbProject.getRepositoryReferences() != null) {
            for (RepositoryRefType repositoryRef : jaxbProject.getRepositoryReferences()
                    .getRepositoryRef()) {
                repositoryUrls.put(repositoryRef.getRepositoryId(), repositoryRef.getValue());
            }
        }

        // Check each repository ID to determine if it is new; attempt to register new remote
        // repositories using the associated URLs.
        for (String repositoryId : repositoryIds) {
            Repository r = repositoryManager.getRepository(repositoryId);
            String repositoryUrl = repositoryUrls.get(repositoryId);

            if ((r == null) && (repositoryUrl != null)) {
                boolean success = false;
                try {
                    repositoryManager.addRemoteRepository(repositoryUrl);
                    success = true;

                } catch (Throwable t) {
                    // No error; post a warning and continue

                } finally {
                    if (!success) {
                        loaderFindings.addFinding(FindingType.WARNING, new FileValidationSource(
                                projectFile),
                                LoaderValidationMessageKeys.WARNING_INVALID_REPOSITORY_URL,
                                repositoryUrl);
                    }
                }
            }
        }
    }

    /**
     * Saves the contents of the specified project to the local file system.
     * 
     * @param project
     *            the project instance to save
     * @throws LibrarySaveException
     */
    public void saveProject(Project project) throws LibrarySaveException {
        saveProject(project, null);
    }

    /**
     * Saves the contents of the specified project to the local file system. In addition to saving
     * the project itself, the contents of all <code>TLLibrary</code> project items are also saved.
     * 
     * @param project
     *            the project instance to save
     * @param findings
     *            validation findings where errors/warnings from the save operation will be reported
     * @throws LibrarySaveException
     */
    public void saveProject(Project project, ValidationFindings findings)
            throws LibrarySaveException {
        saveProject(project, true, findings);
    }

    /**
     * Saves the contents of the specified project to the local file system. In addition to saving
     * the project itself, the contents of all <code>TLLibrary</code> project items are also saved.
     * 
     * @param project
     *            the project instance to save
     * @param saveEditableLibraries
     *            flag indicating whether the unmanaged/WIP library items are to be saved as well
     * @param findings
     *            validation findings where errors/warnings from the save operation will be reported
     * @throws LibrarySaveException
     */
    public void saveProject(Project project, boolean saveUnmanagedLibraries,
            ValidationFindings findings) throws LibrarySaveException {
        if (project.getProjectManager() != this) {
            throw new IllegalArgumentException(
                    "The project is owned by another project manager instance.");
        }
        if (project == builtInProject) {
            throw new IllegalArgumentException("The built-in project cannot be saved.");
        }
        if (findings == null)
            findings = new ValidationFindings();

        if (saveUnmanagedLibraries) {
            List<TLLibrary> libraryList = new ArrayList<TLLibrary>();

            for (ProjectItem item : project.getProjectItems()) {
                RepositoryItemState itemState = item.getState();

                if ((itemState == RepositoryItemState.UNMANAGED)
                        || (itemState == RepositoryItemState.MANAGED_WIP)) {
                    if ((item.getContent() instanceof TLLibrary)
                            && !((TLLibrary) item.getContent()).isReadOnly()) {
                        libraryList.add((TLLibrary) item.getContent());
                    }
                }
            }
            findings.addAll(new LibraryModelSaver().saveLibraries(libraryList));
        }
        ProjectFileUtils.saveProjectFile(project);
    }

    /**
     * Closes the specified project and removes it from this <code>ProjectManager</code> workspace.
     * 
     * <p>
     * NOTE: The contents of the project are not saved by this method. It is the caller's
     * responibility to save the project (if required) before calling this method to close it.
     * 
     * @param project
     *            the project to close
     */
    public void closeProject(Project project) {
        if (project.getProjectManager() != this) {
            throw new IllegalArgumentException(
                    "The project is owned by another project manager instance.");
        }
        if (project == builtInProject) {
            throw new IllegalArgumentException("The built-in project cannot be closed.");
        }
        projects.remove(project);
        purgeOrphanedProjectItems();
    }

    /**
     * Closes all projects and project items and re-initializes the contents of the model. All
     * references to existing project-items, projects, and libraries (including the built-ins)
     * should be considered invalid after calling this method.
     */
    public void closeAll() {
        projects.clear();
        projectItems.clear();
        model.clearModel();
        projects.add(builtInProject = new BuiltInProject(this));
    }

    /**
     * Returns the list of all items maintained by this project manager.
     * 
     * @return List<ProjectItem>
     */
    public List<ProjectItem> getAllProjectItems() {
        return Collections.unmodifiableList(projectItems);
    }

    /**
     * Returns the <code>ProjectItem</code> that represents the local instance of the given library.
     * 
     * @param library
     *            the library for which to return the associated project item
     * @return ProjectItem
     */
    public ProjectItem getProjectItem(AbstractLibrary library) {
        ProjectItem result = null;

        if (library != null) {
            for (ProjectItem item : projectItems) {
                if (item.getContent() == library) {
                    result = item;
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Returns the <code>ProjectItem</code> that represents the local instance of the given library
     * URL.
     * 
     * @param libraryUrl
     *            the library URL for which to return the associated project item
     * @return ProjectItem
     */
    public ProjectItem getProjectItem(URL libraryUrl) {
        return (libraryUrl == null) ? null : getProjectItem(model.getLibrary(libraryUrl));
    }

    /**
     * Returns the version chain for the given project item. The resulting list includes all
     * dependent versions starting with the one provided and ending with the major version that
     * started the chain. If the given project item does not represent a <code>TLLibrary</code>,
     * only the given item will be returned in the resulting list.
     * 
     * @param item
     *            the project item for which to return the version chain
     * @return List<ProjectItem>
     * @throws VersionSchemeException
     *             thrown if the project item's version scheme is not recognized
     */
    public List<ProjectItem> getVersionChain(ProjectItem item) throws VersionSchemeException {
        List<ProjectItem> versionChain = new ArrayList<ProjectItem>();

        if (item != null) {
            if (item.getContent() instanceof TLLibrary) {
                List<TLLibrary> libraryChain = new MajorVersionHelper()
                        .getVersionChain((TLLibrary) item.getContent());

                for (TLLibrary library : libraryChain) {
                    ProjectItem chainItem = getProjectItem(library);

                    if (chainItem != null) {
                        versionChain.add(chainItem);
                    }
                }
            } else {
                versionChain.add(item);
            }
        }
        return versionChain;
    }

    /**
     * Returns the collection of projects to which the given project item is assigned.
     * 
     * @param item
     *            the project item to analyze
     * @return Collection<Project>
     */
    public Collection<Project> getAssignedProjects(ProjectItem item) {
        Collection<Project> projectList = new ArrayList<Project>();

        for (Project project : projects) {
            if (project.getProjectItems().contains(item)) {
                projectList.add(project);
            }
        }
        return projectList;
    }

    /**
     * Adds the managed repository item as a <code>ProjectItem</code> and assigns it to the
     * specified model project.
     * 
     * @param item
     *            the repository item to add
     * @param project
     *            the project to which the repository item should be added
     * @return ProjectItem
     * @throws LibraryLoaderException
     *             thrown if a problem occurs during the library loading process
     * @throws RepositoryException
     *             thrown if one or more managed repository items cannot be accessed
     */
    public ProjectItem addManagedProjectItem(RepositoryItem item, Project project)
            throws LibraryLoaderException, RepositoryException {
        List<ProjectItem> itemList = addManagedProjectItems(
                Arrays.asList(new RepositoryItem[] { item }), project);
        return (itemList.size() == 0) ? null : itemList.get(0);
    }

    /**
     * Adds the managed repository items as <code>ProjectItems</code> and assigns each of them to
     * the specified model project.
     * 
     * @param item
     *            the managed repository items to add
     * @param project
     *            the project to which the repository item should be added
     * @return List<ProjectItem>
     * @throws LibraryLoaderException
     *             thrown if a problem occurs during the library loading process
     * @throws RepositoryException
     *             thrown if one or more managed repository items cannot be accessed
     */
    public List<ProjectItem> addManagedProjectItems(List<RepositoryItem> items, Project project)
            throws LibraryLoaderException, RepositoryException {
        return addManagedProjectItems(items, project, null);
    }

    /**
     * Adds the managed repository items as <code>ProjectItems</code> and assigns each of them to
     * the specified model project.
     * 
     * @param item
     *            the managed repository items to add
     * @param project
     *            the project to which the repository item should be added
     * @param findings
     *            validation findings where errors/warnings from the loading operation will be
     *            reported
     * @return List<ProjectItem>
     * @throws LibraryLoaderException
     *             thrown if a problem occurs during the library loading process
     * @throws RepositoryException
     *             thrown if one or more managed repository items cannot be accessed
     */
    public List<ProjectItem> addManagedProjectItems(List<RepositoryItem> items, Project project,
            ValidationFindings findings) throws LibraryLoaderException, RepositoryException {
        ValidationFindings loaderFindings = new ValidationFindings();
        List<ProjectItem> projectItems = loadAllProjectItems(new ArrayList<File>(), items, project,
                loaderFindings);

        // Validate for errors/warnings if requested by the caller
        if (findings != null) {
            findings.addAll(loaderFindings);
            findings.addAll(TLModelValidator.validateModel(model,
                    ValidatorFactory.COMPILE_RULE_SET_ID));
        }
        return projectItems;
    }

    /**
     * Creates a new <code>ProjectItem</code> to represent the given library and adds it to the
     * contents of the project as an <code>UNMANAGED</code> repository item. This method assumes
     * that the library is an uncontrolled artifact (not currently under repository control), and
     * has already been incorporated into the existing model that is maintained by this
     * <code>ProjectManager</code>.
     * 
     * @param libraryFile
     *            the file content for the library (or legacy schema) to add
     * @param project
     *            the project to which the unmanaged project item will be added
     * @return ProjectItem
     * @throws RepositoryException
     *             thrown if the given library is currently under the control of a repository
     */
    public ProjectItem addUnmanagedProjectItem(AbstractLibrary library, Project project)
            throws RepositoryException {
        if (library.getLibraryUrl() == null) {
            throw new RepositoryException(
                    "Unable to add the given library as an unmanaged item because it has not yet been saved.");
        }
        if (isRepositoryUrl(library.getLibraryUrl())) {
            throw new RepositoryException(
                    "Unable to add the given library as an unmanaged item since it is already under repository control.");
        }
        ProjectItem projectItem = null;

        // Check to see if a project item for this library already exists
        for (ProjectItem item : projectItems) {
            if (library == item.getContent()) {
                projectItem = item;
                break;
            }
        }

        // If no project item exists yet, create one automatically
        if (projectItem == null) {
            projectItem = ProjectItemImpl.newUnmanagedItem(
                    URLUtils.toFile(library.getLibraryUrl()), library, this);

            if (model.getLibrary(library.getLibraryUrl()) == null) {
                try {
                    model.addLibrary(library);
                    new LibraryModelLoader<InputStream>(model).resolveModelEntityReferences();

                } catch (LibraryLoaderException e) {
                    // Should never happen, but just in case...
                    throw new RepositoryException("Error resolving references during library add.",
                            e);
                }
            }
        }

        // If the new (or existing) project item is not yet assigned to the project, assign it now
        if (!project.isMemberOf(projectItem)) {
            projectItems.add(projectItem);
            project.add(projectItem);
        }

        // If required, save the project we just modified
        if (autoSaveProjects) {
            try {
                saveProject(project, false, null);

            } catch (LibrarySaveException e) {
                log.error("Error saving updates to project: " + project.getProjectId(), e);
            }
        }
        return projectItem;
    }

    /**
     * Creates a new <code>ProjectItem</code> to represent the given library file (.otm or .xsd),
     * and adds it to the contents of the project as an <code>UNMANAGED</code> repository item.
     * 
     * @param libraryFile
     *            the file content for the library (or legacy schema) to add
     * @param project
     *            the project to which the unmanaged project item will be added
     * @return ProjectItem
     * @throws LibraryLoaderException
     *             thrown if a problem occurs during the library loading process
     * @throws RepositoryException
     *             thrown if one or more managed repository items cannot be accessed
     */
    public ProjectItem addUnmanagedProjectItem(File libraryFile, Project project)
            throws LibraryLoaderException, RepositoryException {
        List<ProjectItem> itemList = addUnmanagedProjectItems(
                Arrays.asList(new File[] { libraryFile }), project);
        return (itemList.size() == 0) ? null : itemList.get(0);
    }

    /**
     * Creates a new <code>ProjectItem</code> to represent the given library file (.otm or .xsd),
     * and adds it to the contents of the project as an <code>UNMANAGED</code> repository item.
     * 
     * @param libraryFile
     *            the file content for the library (or legacy schema) to add
     * @param project
     *            the project to which the unmanaged project item will be added
     * @return List<ProjectItem>
     * @throws LibraryLoaderException
     *             thrown if a problem occurs during the library loading process
     * @throws RepositoryException
     *             thrown if one or more managed repository items cannot be accessed
     */
    public List<ProjectItem> addUnmanagedProjectItems(List<File> libraryFiles, Project project)
            throws LibraryLoaderException, RepositoryException {
        return addUnmanagedProjectItems(libraryFiles, project, null);
    }

    /**
     * Creates new <code>ProjectItems</code> to represent each of the given library files (.otm or
     * .xsd), and adds them to the contents of the project as <code>UNMANAGED</code> repository
     * items.
     * 
     * @param libraryFiles
     *            the file content for the unmanaged libraries (and/or legacy schemas) to add
     * @param project
     *            the project to which the unmanaged project item will be added
     * @param findings
     *            validation findings where errors/warnings from the loading operation will be
     *            reported
     * @return List<ProjectItem>
     * @throws LibraryLoaderException
     *             thrown if a problem occurs during the library loading process
     * @throws RepositoryException
     *             thrown if one or more managed repository items cannot be accessed
     */
    public List<ProjectItem> addUnmanagedProjectItems(List<File> libraryFiles, Project project,
            ValidationFindings findings) throws LibraryLoaderException, RepositoryException {
        ValidationFindings loaderFindings = new ValidationFindings();
        List<ProjectItem> projectItems = loadAllProjectItems(libraryFiles,
                new ArrayList<RepositoryItem>(), project, loaderFindings);

        // Validate for errors/warnings if requested by the caller
        if (findings != null) {
            findings.addAll(loaderFindings);
            findings.addAll(TLModelValidator.validateModel(model,
                    ValidatorFactory.COMPILE_RULE_SET_ID));
        }
        return projectItems;
    }

    /**
     * Creates new <code>ProjectItems</code> to represent each of the managed and unmanaged library
     * resources (.otm or .xsd), and adds them to the contents of the project.
     * 
     * @param libraryFiles
     *            the file content for the unmanaged libraries (and/or legacy schemas) to add
     * @param managedItems
     *            the managed repository items to be included in the project
     * @param project
     *            the project to which the unmanaged project item will be added
     * @param loaderFindings
     *            validation findings where errors/warnings from the loading operation will be
     *            reported
     * @return List<ProjectItem>
     * @throws LibraryLoaderException
     *             thrown if a problem occurs during the library loading process
     * @throws RepositoryException
     *             thrown if one or more managed repository items cannot be accessed
     */
    private List<ProjectItem> loadAllProjectItems(List<File> libraryFiles,
            List<RepositoryItem> managedItems, Project project, ValidationFindings loaderFindings)
            throws LibraryLoaderException, RepositoryException {
        LibraryModelLoader<InputStream> modelLoader = new LibraryModelLoader<InputStream>(model);
        List<ProjectItem> newItems = new ArrayList<ProjectItem>();

        // Initialize the loader and validation findings
        modelLoader.setResolveModelReferences(false);
        
    	// Optimization that will pre-process the repository URL's for each library.  This will
    	// allow the loader to bypass multiple downloads of the libraries from the remote repository.
        if ((managedItems != null) && !managedItems.isEmpty()) {
        	LibraryNamespaceResolver nsResolver = new DefaultLibraryNamespaceResolver();
        	
            modelLoader.setNamespaceResolver( nsResolver );
            
            for (RepositoryItem managedItem : managedItems) {
            	String repositoryUri = "otm://" + managedItem.getRepository().getId() + "/" + managedItem.getFilename();
            	String libraryUrl = repositoryManager.getContentLocation( managedItem ).toExternalForm();
            	
            	nsResolver.setRepositoryLocation( repositoryUri, libraryUrl );
            }
        }
        
        // Load all of the managed repository items
        for (RepositoryItem managedItem : managedItems) {
            URL libraryUrl = repositoryManager.getContentLocation(managedItem);
            ProjectItem newItem = null;

            // Load the library from the local file system if it does not already exist in the model
            if (!model.hasLibrary(libraryUrl)) {
                try {
                    modelLoader.loadLibraryModel(new LibraryStreamInputSource(libraryUrl));

                    AbstractLibrary managedLibrary = model.getLibrary(libraryUrl);

                    if (managedLibrary != null) {
                        // Check the namespace of the library we just loaded and make sure it
                        // matches the
                        // repository item; if not, reassign the libary's namespace and issue a
                        // loader warning.
                        if (!managedItem.getNamespace().equals(managedLibrary.getNamespace())) {
                            try {
                                managedLibrary.setNamespace(managedItem.getNamespace());
                                loaderFindings
                                        .addFinding(
                                                FindingType.WARNING,
                                                new LibraryValidationSource(managedLibrary),
                                                LoaderValidationMessageKeys.WARNING_MANAGED_LIBRARY_NAMESPACE_MISMATCH,
                                                managedItem.getFilename());

                            } catch (IllegalArgumentException e) {
                                // If we cannot reassign the namespace because a duplicate exists,
                                // remove
                                // the library and issue a loader error.
                                loaderFindings
                                        .addFinding(
                                                FindingType.ERROR,
                                                new LibraryValidationSource(managedLibrary),
                                                LoaderValidationMessageKeys.ERROR_MANAGED_LIBRARY_NAMESPACE_ERROR,
                                                managedItem.getFilename());
                            }
                        }
                        newItem = newManagedProjectItem(managedItem, managedLibrary);
                    }

                } catch (Throwable t) {
                    if (loaderFindings != null) {
                        loaderFindings
                                .addFinding(
                                        FindingType.ERROR,
                                        new FileValidationSource(project.getProjectFile()),
                                        LoaderValidationMessageKeys.ERROR_UNKNOWN_EXCEPTION_DURING_PROJECT_LOAD,
                                        URLUtils.getShortRepresentation(libraryUrl), ExceptionUtils
                                                .getExceptionClass(t).getSimpleName(),
                                        ExceptionUtils.getExceptionMessage(t));
                    }
                }

            } else { // Otherwise, locate the existing project item
                newItem = findOrCreateProjectItem(model.getLibrary(libraryUrl));
            }

            if (newItem != null) {
                projectItems.add(newItem);
                project.add(newItem);
                newItems.add(newItem);
            }
        }

        // Load all of the unmanaged library files
        for (File libraryFile : libraryFiles) {
            URL libraryUrl = URLUtils.toURL(libraryFile);
            ProjectItem newItem = null;

            if (!libraryFile.exists()) {
                loaderFindings.addFinding(FindingType.ERROR, new FileValidationSource(libraryFile),
                        LoaderValidationMessageKeys.ERROR_INVALID_PROJECT_ITEM_LOCATION,
                        libraryFile.getAbsolutePath());

            } else if (!model.hasLibrary(libraryUrl)) { // Load the library from the local file
                                                        // system if it does not already exist in
                                                        // the model
                try {
                    modelLoader.loadLibraryModel(new LibraryStreamInputSource(libraryUrl));

                    AbstractLibrary unmanagedLibrary = model.getLibrary(libraryUrl);

                    if (unmanagedLibrary != null) {
                        newItem = ProjectItemImpl.newUnmanagedItem(URLUtils.toFile(libraryUrl),
                                unmanagedLibrary, this);
                    }

                } catch (Throwable t) {
                    if (loaderFindings != null) {
                        loaderFindings
                                .addFinding(
                                        FindingType.ERROR,
                                        new FileValidationSource(project.getProjectFile()),
                                        LoaderValidationMessageKeys.ERROR_UNKNOWN_EXCEPTION_DURING_PROJECT_LOAD,
                                        URLUtils.getShortRepresentation(libraryUrl), ExceptionUtils
                                                .getExceptionClass(t).getSimpleName(),
                                        ExceptionUtils.getExceptionMessage(t));
                    }
                }
            } else { // Otherwise, locate the existing project item
                newItem = findOrCreateProjectItem(model.getLibrary(libraryUrl));
            }

            if (newItem != null) {
                projectItems.add(newItem);
                project.add(newItem);
                newItems.add(newItem);
            }
        }

        // Searching for any newly-discovered dependencies that need to be added to the project
        if (loaderFindings != null) {
            loaderFindings.addAll(modelLoader.getLoaderFindings());
        }
        modelLoader.resolveModelEntityReferences();
        updateProjectDependencies(project, newItems);

        // Update all import/include references in the project, just in case any have changed since
        // the last time this/these libraries were loaded.
        for (ProjectItem item : projectItems) {
            if (item.getContent() instanceof TLLibrary) {
                ImportManagementIntegrityChecker.verifyReferencedLibraries((TLLibrary) item
                        .getContent());
            }
        }

        // In some cases, libraries may have been referenced by includes/imports, but no real
        // dependencies
        // to their content exist. In these cases, libraries (project-items) that are not explicitly
        // assigned
        // to a project need to be purged.
        purgeOrphanedProjectItems();

        if (autoSaveProjects) {
            saveAffectedProjects(newItems);
        }
        return newItems;
    }

    /**
     * Publishes the given <code>UNMANAGED</code> item to the specified remote repository. Once
     * added, the item will be replaced by a reference to the newly-created
     * <code>MANAGED_LOCKED</code> item in the repository.
     * 
     * @param item
     *            the item to be added as a managed asset of the repository
     * @param repository
     *            the repository to which the given item will be published
     * @throws IllegalStateException
     *             thrown if the project item's state is not <code>UNMANAGED</code>
     * @throws RepositoryException
     *             thrown if the remote repository cannot be accessed or an error occurs during
     *             publication
     * @throws PublishWithLocalDependenciesException
     *             thrown if the project item to be published contains a local file reference
     */
    public void publish(ProjectItem item, Repository repository)
            throws PublishWithLocalDependenciesException, RepositoryException {
        publish(Arrays.asList(new ProjectItem[] { item }), repository);
    }

    /**
     * Publishes the given <code>UNMANAGED</code> item to the specified remote repository. Once
     * added, the item will be replaced by a reference to the newly-created
     * <code>MANAGED_LOCKED</code> item in the repository.
     * 
     * @param items
     *            the collection of items to be added as managed assets of the repository
     * @param repository
     *            the repository to which the given items will be published
     * @throws IllegalStateException
     *             thrown if a project item's state is not <code>UNMANAGED</code>
     * @throws RepositoryException
     *             thrown if the remote repository cannot be accessed or an error occurs during
     *             publication
     * @throws PublishWithLocalDependenciesException
     *             thrown if one or more of the project items to be published contains a local file
     *             reference
     */
    public void publish(Collection<ProjectItem> items, Repository repository)
            throws PublishWithLocalDependenciesException, RepositoryException {
        // Check repository state of each item to make sure it can be published; also make sure that
        // the
        // user has write access to each item's namespace in the target repository
        Set<String> authorizedNamespaces = new HashSet<String>();

        for (ProjectItem item : items) {
            if (item.getState() != RepositoryItemState.UNMANAGED) {
                throw new RepositoryException(
                        "Only unmanaged items can be published to a repository.");
            }
            if (!authorizedNamespaces.contains(item.getBaseNamespace())) {
                if (repository.getUserAuthorization(item.getBaseNamespace()) == RepositoryPermission.WRITE) {
                    authorizedNamespaces.add(item.getBaseNamespace());

                } else {
                    throw new RepositoryException(
                            "The user does not have write permission for namespace '"
                                    + item.getBaseNamespace() + "'.");
                }
            }
        }

        // Also be sure that no dependencies on unmanaged files exist before proceeding
        Collection<ProjectItem> unmanagedReferences = new ArrayList<ProjectItem>();

        findUnmanagedReferences(items, unmanagedReferences);

        if (!unmanagedReferences.isEmpty()) {
            for (ProjectItem item : items) {
                if (!unmanagedReferences.contains(item)) {
                    unmanagedReferences.add(item);
                }
            }
            throw new PublishWithLocalDependenciesException(
                    "One or more libraries contains references to unmanaged content.  "
                            + "Confirm publication of dependent libraries and resubmit.", items,
                    unmanagedReferences);
        }

        // Perform the final preparation of all items for publication
        prepareForPublication(items, repository);

        // Publish each item's content to the repository and download a copy to the local cache
        Collection<ProjectItem> successfullyPublishedItems = new ArrayList<ProjectItem>();
        Map<AbstractLibrary, URL> originalLibraryUrls = new HashMap<AbstractLibrary, URL>();
        boolean allItemsSuccessful = false;

        try {
            for (ProjectItem item : items) {
                File contentFile = URLUtils.toFile(item.getContent().getLibraryUrl());
                File tempFile = null;
                boolean currentItemSuccessful = false;

                // Store the original URL of each library, just in case we need to roll back later
                originalLibraryUrls.put(item.getContent(), item.getContent().getLibraryUrl());

                try {
                    // Start by saving the current content of the library to a temporary location
                    // (TLLibrary only)
                    // NOTE: Saving to a temp file avoids the problem of stomping the original
                    // backup file we created
                    // during the preparation step above.
                    File libraryFile;

                    if (item.getContent() instanceof TLLibrary) {
                        try {
                            libraryFile = tempFile = File.createTempFile("publishTemp", ".otm");

                            ((TLLibrary) item.getContent()).setLibraryUrl(URLUtils.toURL(tempFile));
                            new LibraryModelSaver().saveLibrary((TLLibrary) item.getContent());

                        } catch (LibrarySaveException e) {
                            throw new RepositoryException("Error saving current state of library: "
                                    + item.getFilename());
                        }

                    } else {
                        libraryFile = contentFile;
                    }

                    // Publish the library to the repository
                    InputStream contentStream = new FileInputStream(libraryFile);
                    TLLibraryStatus initialStatus = TLLibraryStatus.DRAFT;
                    String versionScheme = null;

                    if (item.getContent() instanceof TLLibrary) {
                        TLLibrary library = (TLLibrary) item.getContent();

                        initialStatus = library.getStatus();
                        versionScheme = library.getVersionScheme();
                    }
                    RepositoryItem repositoryItem = repository.publish(contentStream,
                            getPublicationFilename(item.getContent()), item.getLibraryName(),
                            item.getNamespace(), item.getVersion(), versionScheme, initialStatus);

                    // Change the library URL to be the copy we just moved into the local repository
                    URL managedLibraryUrl = repositoryManager.getContentLocation(repositoryItem);
                    ProjectItemImpl managedItem = (ProjectItemImpl) item;

                    managedItem.getContent().setLibraryUrl(managedLibraryUrl);
                    managedItem.setState(RepositoryItemState.MANAGED_UNLOCKED);
                    managedItem.setRepository(repository);

                    // Save the changes to the project state
                    if (autoSaveProjects && (item != null)) {
                        saveAffectedProjects(item);
                    }

                    // Rename our original file to ".bak" since the 'real' content is now managed
                    // by the repository.
                    File backupFile = getBackupFile(contentFile);

                    if (backupFile.exists()) { // delete the old backup, if one exists
                        backupFile.delete();
                    }
                    contentFile.renameTo(backupFile);

                    currentItemSuccessful = true;

                } catch (IOException e) {
                    throw new RepositoryException("Unable to publish unmanaged project content: "
                            + item.getFilename(), e);

                } finally {
                    // Delete the temp file we created prior to publication
                    if (tempFile != null)
                        tempFile.delete();

                    // If unsuccessful, we need to change the library's URL back to its original
                    // value
                    if (!currentItemSuccessful && (item.getContent() instanceof TLLibrary)) {
                        ((TLLibrary) item.getContent()).setLibraryUrl(URLUtils.toURL(contentFile));
                    }
                }
            }

        } finally {
            if (allItemsSuccessful) {
                // Since each of the files are now managed, we need to update the other models in
                // the
                // project to reference them using a repository URI for the import/include file
                // hints
                Collection<Project> affectedProjects = new HashSet<Project>();
                Set<ProjectItem> affectedProjectItems = new HashSet<ProjectItem>();

                for (ProjectItem item : items) {
                    affectedProjects.addAll(getAssignedProjects(item));
                }
                for (Project project : affectedProjects) {
                    for (ProjectItem affectedItem : project.getProjectItems()) {
                        if (!items.contains(affectedItem)) {
                            affectedProjectItems.add(affectedItem);
                        }
                    }
                }
                for (ProjectItem affectedItem : affectedProjectItems) {
                    if (affectedItem.getContent() instanceof TLLibrary) {
                        TLLibrary affectedLibrary = (TLLibrary) affectedItem.getContent();

                        if (!affectedLibrary.isReadOnly()) {
                            ImportManagementIntegrityChecker
                                    .verifyReferencedLibraries(affectedLibrary);
                        }
                    }
                }

            } else {
                // Rollback the changes that were made to each of the libraries, and delete any
                // items
                // that were successfully published from the repository
                for (ProjectItem rollbackItem : successfullyPublishedItems) {
                    ProjectItemImpl unmanagedItem = (ProjectItemImpl) rollbackItem;

                    // Delete the item from the repository
                    try {
                        repository.delete(rollbackItem);

                    } catch (Throwable t) {
                        log.warn(
                                "Error during publicaton rollback - unable to delete item from the repository: "
                                        + rollbackItem.getFilename(), t);
                    }

                    // Reset the properties of the local project item to their original state
                    unmanagedItem.getContent().setLibraryUrl(
                            originalLibraryUrls.get(unmanagedItem.getContent()));
                    unmanagedItem.setState(RepositoryItemState.UNMANAGED);
                    unmanagedItem.setRepository(null);

                }
            }
        }
    }

    /**
     * Searches the model for dependencies of the given project item and adds them to the collection
     * of unmanaged references.
     * 
     * @param item
     *            the item to check for dependencies
     * @param pendingPublicationItems
     *            the list of all items to be published that are currently unmanaged
     * @param unmanagedReferences
     *            the collection of unmanaged references
     */
    private void findUnmanagedReferences(Collection<ProjectItem> pendingPublicationItems,
            Collection<ProjectItem> unmanagedReferences) {
        for (ProjectItem item : pendingPublicationItems) {
            Collection<ProjectItem> itemList = new HashSet<ProjectItem>();
            int unmanagedReferenceCount = -1;

            itemList.add(item); // 1st loop - just analyze the new item

            while (unmanagedReferences.size() != unmanagedReferenceCount) { // continue until our
                                                                            // count does not change
                Collection<ProjectItem> newUnmanagedReferences = new HashSet<ProjectItem>();

                unmanagedReferenceCount = unmanagedReferences.size();

                for (ProjectItem currentItem : itemList) {
                    if (currentItem.getContent() instanceof TLLibrary) {
                        List<AbstractLibrary> referencedLibraries = ImportManagementIntegrityChecker
                                .getReferencedLibraries((TLLibrary) currentItem.getContent());

                        for (AbstractLibrary referencedLib : referencedLibraries) {
                            if (referencedLib instanceof BuiltInLibrary)
                                continue;
                            ProjectItem referencedItem = getProjectItem(referencedLib);

                            if (referencedItem == null) {
                                throw new IllegalStateException(
                                        "A dependent library is referenced that is not under the control of the local Project Manager: "
                                                + referencedLib.getName());

                            } else if (!pendingPublicationItems.contains(referencedItem)
                                    && (referencedItem.getState() == RepositoryItemState.UNMANAGED)
                                    && !newUnmanagedReferences.contains(referencedItem)) {
                                newUnmanagedReferences.add(referencedItem);
                            }
                        }
                    }
                }

                // Add the newly-found unmanaged dependencies to the list and continue looping until
                // we do not find any new dependencies.
                for (ProjectItem urItem : newUnmanagedReferences) {
                    if (!unmanagedReferences.contains(urItem)) {
                        unmanagedReferences.add(urItem);
                    }
                }
                itemList.clear();
                itemList.addAll(newUnmanagedReferences); // subsequent loops - look for dependencies
                                                         // of the items we just found
            }
        }
    }

    /**
     * Prepares each of the items that are pending publication by updating their includes and
     * imports to reflect repository URI locations. This method also saves the original state of
     * each library prior to adjusting its imports and includes.
     * 
     * @param pendingPublicationItems
     *            the list of all items to be published that are currently unmanaged
     * @param repository
     *            the repository to which the pending items are to be published
     * @throws RepositoryException
     *             thrown if the namespace URI for any of the pending items is invalid
     */
    private void prepareForPublication(Collection<ProjectItem> pendingPublicationItems,
            Repository repository) throws RepositoryException {
        Collection<TLLibrary> preparedLibraries = new ArrayList<TLLibrary>();
        boolean success = false;
        try {
            Map<AbstractLibrary, URL> libraryUrlOverrides = new HashMap<AbstractLibrary, URL>();

            for (ProjectItem item : pendingPublicationItems) {
                // Calculate the URL for the local repository location of the item (needed for the
                // next step).
                File localRepositoryLocation = repositoryManager.getFileManager()
                        .getLibraryContentLocation(item.getBaseNamespace(), item.getFilename(),
                                item.getVersion());

                libraryUrlOverrides.put(item.getContent(), URLUtils.toURL(localRepositoryLocation));

                // A bit of a hack - we need to trick the import/include updater into thinking that
                // the
                // items to be published have already been processed
                if (item.getContent() instanceof TLLibrary) {
                    ((ProjectItemImpl) item).setRepository(repository);
                    ((ProjectItemImpl) item).setState(RepositoryItemState.MANAGED_UNLOCKED);
                }
            }

            // Automatically save each library and update the includes/imports to reflect repository
            // URI
            // references where necessary
            for (ProjectItem item : pendingPublicationItems) {
                if (item.getContent() instanceof TLLibrary) {
                    TLLibrary itemLibrary = (TLLibrary) item.getContent();

                    try {
                        new LibraryModelSaver().saveLibrary(itemLibrary);

                    } catch (LibrarySaveException e) {
                        throw new RepositoryException("Error saving current state of library: "
                                + item.getFilename());
                    }
                    ImportManagementIntegrityChecker.verifyReferencedLibraries(itemLibrary,
                            libraryUrlOverrides);
                    preparedLibraries.add(itemLibrary);
                }
            }
            success = true;

        } finally {
            // Regardless of the outcome, we need to undo the hack that we used to trick the
            // import/include updater
            for (ProjectItem item : pendingPublicationItems) {
                if (item instanceof ProjectItemImpl) {
                    ((ProjectItemImpl) item).setRepository(null);
                    ((ProjectItemImpl) item).setState(RepositoryItemState.UNMANAGED);
                }
            }

            // If we were not successful, revert the changes we made to the import/includes of each
            // library
            if (!success) {
                for (TLLibrary library : preparedLibraries) {
                    ImportManagementIntegrityChecker.verifyReferencedLibraries(library);
                }
            }
        }
    }

    /**
     * Locks the given <code>ProjectItem</code> for editing by the local user. When an item is
     * locked, the reference to the remote repository item is replaced with a work-in-process (WIP)
     * copy of the library file in the local workspace.
     * 
     * @param item
     *            the repository item to lock
     * @param wipFolder
     *            the folder location where the work-in-process file is to be created
     * @throws IllegalStateException
     *             thrown if the project item's state is not <code>MANAGED_UNLOCKED</code>
     * @throws RepositoryException
     */
    public void lock(ProjectItem item, File wipFolder) throws RepositoryException {
        boolean success = false;
        File backupFile = null;
        File wipFile = null;
        try {
            // Refresh the content from the remote repository to make sure we are working with the
            // most current data
            if (item.getRepository() instanceof RemoteRepository) {
                ((RemoteRepository) item.getRepository()).downloadContent(item, true);
            }

            // Perform validation checks
            switch (item.getState()) {
                case MANAGED_LOCKED:
                    throw new RepositoryException(
                            "Unable to lock - the item is already locked by another user.");

                case MANAGED_WIP:
                    throw new RepositoryException(
                            "Unable to lock - the item is already locked by the local user.");

                case UNMANAGED:
                    throw new RepositoryException(
                            "Unable to lock - the item is not a managed artifact.");
            }

            // Copy the repository file to the WIP location
            File repositoryFile = URLUtils.toFile(item.getContent().getLibraryUrl());

            wipFile = repositoryManager.getFileManager().getLibraryWIPContentLocation(
                    item.getBaseNamespace(), item.getFilename());
            backupFile = ProjectFileUtils.createBackupFile(wipFile);
            ProjectFileUtils.copyFile(repositoryFile, wipFile);

            // Call the remote web service to obtain the lock
            ProjectItemImpl managedItem = (ProjectItemImpl) item;

            if (managedItem.getRepository() == repositoryManager) {
                managedItem.setLockedByUser(System.getProperty("user.name"));
            }
            repositoryManager.lock(managedItem);

            // Update the project item and its library to reference the WIP file instead of the
            // managed repository item.
            managedItem.getContent().setLibraryUrl(URLUtils.toURL(wipFile));

            // Save the changes to the project state
            if (autoSaveProjects) {
                saveAffectedProjects(item);
            }
            success = true;

        } catch (IOException e) {
            throw new RepositoryException(
                    "Unable to obtain a lock for the managed project content: "
                            + item.getFilename(), e);

        } finally {
            try {
                if (!success) {
                    // Roll back workspace file changes if we encountered an error
                    if ((wipFile != null) && wipFile.exists())
                        wipFile.delete();
                    if (backupFile != null)
                        ProjectFileUtils.restoreBackupFile(backupFile, wipFile.getName());

                } else {
                    // Purge the backup file if the operation was successful
                    if (backupFile != null)
                        ProjectFileUtils.removeBackupFile(backupFile);
                }
            } catch (Throwable t) {
            }
        }
    }

    /**
     * Unlocks the given work-in-process (WIP) <code>ProjectItem</code>. If the 'commitWIP' flag is
     * true, the conent of the WIP file will be committed to the remote repository before the
     * existing lock is released. If false, any changes in the the WIP content will be discarded.
     * 
     * @param item
     *            the repository item to unlock
     * @param commitWIP
     *            flag indicating whether to commit the existing work-in-process content for the
     *            item
     * @throws IllegalStateException
     *             thrown if the project item's state is not <code>MANAGED_WIP</code>
     * @throws RepositoryException
     */
    public void unlock(ProjectItem item, boolean commitWIP) throws RepositoryException {
        // Refresh the content from the remote repository to make sure we are working with the most
        // current data
        if (item.getRepository() instanceof RemoteRepository) {
            ((RemoteRepository) item.getRepository()).downloadContent(item, true);
        }

        // Perform validation checks
        if (item.getState() != RepositoryItemState.MANAGED_WIP) {
            throw new RepositoryException(
                    "Unable to release lock - the item is not currently locked by the local user.");
        }

        // Call the remote web service to obtain the lock
        ProjectItemImpl managedItem = (ProjectItemImpl) item;

        repositoryManager.unlock(managedItem, commitWIP);

        // Update the project item and its library to reference the managed repository item instead
        // of the WIP file.
        URL managedItemUrl = repositoryManager.getContentLocation(managedItem);

        managedItem.getContent().setLibraryUrl(managedItemUrl);

        // Save the changes to the project state
        if (autoSaveProjects) {
            saveAffectedProjects(item);
        }
    }

    /**
     * Commits the content of the given work-in-process (WIP) <code>ProjectItem</code> to the remote
     * repository, but retains the local user's lock.
     * 
     * @param item
     *            the repository item whose content is to be committed
     * @throws IllegalStateException
     *             thrown if the project item's state is not <code>MANAGED_WIP</code>
     * @throws RepositoryException
     */
    public void commit(ProjectItem item) throws RepositoryException {
        if (item.getState() != RepositoryItemState.MANAGED_WIP) {
            throw new RepositoryException(
                    "Unable to commit - the item is not a work-in-process copy.");
        }

        // Automatically save the library to make sure its file system representation is in-synch
        // with the copy we have in memory
        if (autoSaveProjects) {
            try {
                if (item.getContent() instanceof TLLibrary) {
                    new LibraryModelSaver().saveLibrary((TLLibrary) item.getContent());
                }
            } catch (LibrarySaveException e) {
                throw new RepositoryException(
                        "Error saving library before committing content to the repository.", e);
            }
        }
        repositoryManager.commit(item);
    }

    /**
     * Reverts the contents of the given work-in-process (WIP) <code>ProjectItem</code> to the
     * content currently published in the remote repository. The local user's lock on the file is
     * retained (leaving the item in the <code>MANAGED_WIP</code> state), but all local changes are
     * permanently discarded.
     * 
     * @param item
     *            the repository item whose content is to be committed
     * @throws IllegalStateException
     *             thrown if the project item's state is not <code>MANAGED_WIP</code>
     * @throws RepositoryException
     */
    public void revert(ProjectItem item) throws RepositoryException {
        if (item.getState() != RepositoryItemState.MANAGED_WIP) {
            throw new RepositoryException(
                    "Unable to revert - the item is not a work-in-process copy.");
        }
        repositoryManager.revert(item);
    }

    /**
     * Returns a backup file with the same name and location, but with an extension of ".bak".
     * 
     * @param originalFile
     *            the original file for which to return a backup
     * @return File
     */
    private File getBackupFile(File originalFile) {
        String backupFilename = originalFile.getName();
        int dotIdx = backupFilename.lastIndexOf('.');

        if (dotIdx >= 0) {
            backupFilename = backupFilename.substring(0, dotIdx);
        }
        backupFilename += ".bak";
        return new File(originalFile.getParentFile(), backupFilename);
    }

    /**
     * Promotes a managed <code>ProjectItem</code> from <code>DRAFT</code> status to
     * <code>FINAL</code>. Items must be in the <code>MANAGED_UNLOCKED</code> state in order to be
     * promoted.
     * 
     * @param item
     *            the managed project item to promote
     * @throws RepositoryException
     */
    public void promote(ProjectItem item) throws RepositoryException {
        TLLibraryStatus currentStatus = TLLibraryStatus.FINAL;

        if (item.getContent() instanceof TLLibrary) {
            currentStatus = ((TLLibrary) item.getContent()).getStatus();
        }
        if (item.getState() != RepositoryItemState.MANAGED_UNLOCKED) {
            throw new RepositoryException(
                    "Unable to promote - the item must be a managed resource that not locked for editing.");
        }
        if (currentStatus != TLLibraryStatus.DRAFT) {
            throw new RepositoryException(
                    "Unable to promote - only user-defined libraries in DRAFT status can be promoted.");
        }
        repositoryManager.promote(item);

        if (item.getContent() instanceof TLLibrary) {
            ((TLLibrary) item.getContent()).setStatus(TLLibraryStatus.FINAL);
        }
    }

    /**
     * Promotes a managed <code>ProjectItem</code> from <code>DRAFT</code> status to
     * <code>FINAL</code>. This operation can only be performed if the local user has administrative
     * permissions to modify the requested item, and the item is in the
     * <code>MANAGED_UNLOCKED</code> state.
     * 
     * @param item
     *            the managed project item to demote
     * @throws RepositoryException
     */
    public void demote(ProjectItem item) throws RepositoryException {
        TLLibraryStatus currentStatus = TLLibraryStatus.FINAL;

        if (item.getContent() instanceof TLLibrary) {
            currentStatus = ((TLLibrary) item.getContent()).getStatus();
        }
        if (item.getState() != RepositoryItemState.MANAGED_UNLOCKED) {
            throw new RepositoryException(
                    "Unable to demote - the item must be a managed resource that is not locked for editing.");
        }
        if (currentStatus != TLLibraryStatus.FINAL) {
            throw new RepositoryException(
                    "Unable to demote - only user-defined libraries in FINAL status can be demoted.");
        }
        repositoryManager.demote(item);

        if (item.getContent() instanceof TLLibrary) {
            ((TLLibrary) item.getContent()).setStatus(TLLibraryStatus.DRAFT);
        }
    }

    /**
     * Returns true if the given URL references a location in the user's local repository -- either
     * as a locally-managed item or a local copy of a remotely-managed library.
     * 
     * @param libraryUrl
     *            the library URL to analyze
     * @return boolean
     */
    public boolean isRepositoryUrl(URL libraryUrl) {
        boolean result = false;

        if (URLUtils.isFileURL(libraryUrl)) {
            File repositoryLocation = repositoryManager.getRepositoryLocation();
            File libraryFolder = URLUtils.toFile(libraryUrl).getParentFile();

            while (!result && (libraryFolder != null)) {
                result = libraryFolder.equals(repositoryLocation);
                libraryFolder = libraryFolder.getParentFile();
            }
        }
        return result;
    }

    /**
     * Verifies that the specified project ID is not among the list of currently-open projects.
     * 
     * @param projectFile
     *            the project file to analyze
     * @param existingProject
     *            indicates that this check is for an existing project that should be ignored when
     *            performing the search
     * @throws IllegalArgumentException
     *             thrown if the file and/or ID provided are already in use
     */
    protected void validateProjectID(String projectId, Project existingProject) {
        if ((projectId == null) || (projectId.length() == 0)) {
            throw new IllegalArgumentException("The project ID cannot be null or blank.");
        }
        for (Project project : projects) {
            if (project == existingProject) {
                continue;
            }
            if (projectId.equals(project.getProjectId())) {
                throw new IllegalArgumentException("The specified project ID is already in use: "
                        + projectId);
            }
        }
    }

    /**
     * Verifies that the specified project file is not among the list of currently-open projects.
     * 
     * @param projectFile
     *            the project file to analyze
     * @param existingProject
     *            indicates that this check is for an existing project that should be ignored when
     *            performing the search
     * @throws IllegalArgumentException
     *             thrown if the file and/or ID provided are already in use
     */
    protected void validateProjectFile(File projectFile, Project existingProject) {
        if (projectFile == null) {
            throw new IllegalArgumentException("The project file location cannot be null.");
        }
        for (Project project : projects) {
            if ((project == existingProject)
                    || BuiltInProject.BUILTIN_PROJECT_ID.equals(project.getProjectId())) {
                continue;
            }
            if (projectFile.getAbsolutePath().equals(project.getProjectFile().getAbsolutePath())) {
                throw new IllegalArgumentException("The specified project file is already open: "
                        + projectFile.getAbsolutePath());
            }
        }
    }

    /**
     * Adds any new project items that are required based on the current set of dependencies of the
     * items that currently exist in the project.
     * 
     * @param project
     *            the project to be analyzed and updated
     * @param addedProjectItems
     *            collection where any newly discovered project items will be added (may be null)
     * @throws RepositoryException
     *             thrown if one or more managed repository items cannot be accessed
     */
    private void updateProjectDependencies(Project project,
            Collection<ProjectItem> addedProjectItems) throws RepositoryException {
        Map<String, ProjectItem> globalItemMap = new HashMap<String, ProjectItem>();
        Set<String> existingProjectLibraries = new HashSet<String>();

        // Prepare the set of existing project libraries and a global map of all know project items
        refreshProjectItems();

        for (ProjectItem item : project.getProjectItems()) {
            existingProjectLibraries.add(item.getContent().getLibraryUrl().toExternalForm());
        }
        for (ProjectItem item : projectItems) {
            globalItemMap.put(item.getContent().getLibraryUrl().toExternalForm(), item);
        }

        // Scan for dependencies of the project's existing libraries
        DependencyFilterBuilder builder = new DependencyFilterBuilder()
                .setNavigator(new ProjectItemDependencyNavigator())
                .setIncludeExtendedLegacySchemas(true).setIncludeEntityExtensions(true);

        for (ProjectItem item : project.getProjectItems()) {
            if (item.getContent() != null) {
                builder.addLibrary(item.getContent());
            }
        }
        CodeGenerationFilter filter = builder.buildFilter();

        // Check to see if new dependencies need to be added to the project
        for (AbstractLibrary library : model.getAllLibraries()) {
            if (library instanceof BuiltInLibrary) {
                continue;
            }
            if (!existingProjectLibraries.contains(library.getLibraryUrl().toExternalForm())
                    && filter.processLibrary(library)) {
                ProjectItem itemToAdd = globalItemMap.get(library.getLibraryUrl().toExternalForm());

                if (itemToAdd != null) {
                    if (addedProjectItems != null) {
                        addedProjectItems.add(itemToAdd);
                    }
                    project.add(itemToAdd);
                }
            }
        }
    }

    /**
     * Searches the model for new project items that have not yet been added to the global list, and
     * removes items whose libraries no longer exist in the project.
     * 
     * @throws RepositoryException
     *             thrown if one or more managed repository items cannot be accessed
     */
    private void refreshProjectItems() throws RepositoryException {
        Set<String> existingItemUrls = new HashSet<String>();

        for (ProjectItem item : projectItems) {
            existingItemUrls.add(item.getContent().getLibraryUrl().toExternalForm());
        }
        for (AbstractLibrary library : model.getAllLibraries()) {
            if (library instanceof BuiltInLibrary) {
                continue;
            }
            if (!existingItemUrls.contains(library.getLibraryUrl().toExternalForm())) {
                ProjectItem newItem;

                if (isRepositoryUrl(library.getLibraryUrl())) { // New managed project item
                    newItem = newManagedProjectItem(null, library);

                } else { // New unmanaged project item
                    newItem = ProjectItemImpl.newUnmanagedItem(
                            URLUtils.toFile(library.getLibraryUrl()), library, this);
                }
                projectItems.add(newItem);
            }
        }
    }

    /**
     * Saves all of the affected projects when the given project item is changed in some way that
     * affects its persistent state.
     * 
     * @param item
     *            the project item that was modified
     */
    private void saveAffectedProjects(ProjectItem item) {
        saveAffectedProjects(Arrays.asList(new ProjectItem[] { item }));
    }

    /**
     * Saves all of the affected projects when the given project item is changed in some way that
     * affects its persistent state.
     * 
     * @param item
     *            the project item that was modified
     */
    private void saveAffectedProjects(List<ProjectItem> itemList) {
        Set<Project> affectedProjects = new HashSet<Project>();

        for (ProjectItem item : itemList) {
            for (Project project : item.memberOfProjects()) {
                affectedProjects.add(project);
            }
        }
        for (Project project : affectedProjects) {
            try {
                saveProject(project, false, null);

            } catch (LibrarySaveException e) {
                log.error("Error saving updates to project: " + project.getProjectId(), e);
            }
        }
    }

    /**
     * Searches the global list of project items and removes the ones that are no longer associated
     * with a project. When a project item is removed, the associated library is also removed from
     * the underlying <code>TLModel</code>.
     */
    protected void purgeOrphanedProjectItems() {
        List<ProjectItem> orphanedItems = new ArrayList<ProjectItem>();

        for (ProjectItem item : projectItems) {
            if (item.memberOfProjects().isEmpty()) {
                orphanedItems.add(item);
            }
        }
        for (ProjectItem orphan : orphanedItems) {
            projectItems.remove(orphan);
            model.removeLibrary(orphan.getContent());
        }
    }

    /**
     * Returns the existing <code>ProjectItem</code> that is associated with the specified library,
     * or creates one automatically if no such project item exists yet.
     * 
     * @param library
     *            the library for which to return a project item
     * @return ProjectItem
     * @throws RepositoryException
     *             thrown if one or more managed repository items cannot be accessed
     */
    private ProjectItem findOrCreateProjectItem(AbstractLibrary library) throws RepositoryException {
        ProjectItem projectItem = null;

        for (ProjectItem item : projectItems) {
            if (library == item.getContent()) {
                projectItem = item;
                break;
            }
        }
        if (projectItem == null) {
            if (isRepositoryUrl(library.getLibraryUrl())) { // New managed project item
                projectItem = newManagedProjectItem(null, library);

            } else { // New unmanaged project item
                projectItem = ProjectItemImpl.newUnmanagedItem(
                        URLUtils.toFile(library.getLibraryUrl()), library, this);
            }
            projectItems.add(projectItem);
        }
        return projectItem;
    }

    /**
     * Constructs a new <code>ProjectItem</code> instance using information provided by the given
     * library.
     * 
     * @param repositoryItem
     *            the repository item from which to create the new managed project item
     * @param library
     *            the library from which to create the new managed project item
     * @return ProjectItem
     * @throws RepositoryException
     *             thrown if the library's meta-data cannot be located in any accessible repository
     */
    private ProjectItem newManagedProjectItem(RepositoryItem repositoryItem, AbstractLibrary library)
            throws RepositoryException {
        try {
            if (repositoryItem == null) {
                String baseNamespace = library.getNamespace();
                String versionIdentifier = null;

                if (library instanceof TLLibrary) {
                    TLLibrary userLibrary = (TLLibrary) library;
                    VersionScheme vScheme = VersionSchemeFactory.getInstance().getVersionScheme(
                            userLibrary.getVersionScheme());

                    baseNamespace = vScheme.getBaseNamespace(library.getNamespace());
                    versionIdentifier = userLibrary.getVersion();
                }
                repositoryItem = repositoryManager.getRepositoryItem(baseNamespace,
                        getLibraryUrlFilename(library), versionIdentifier);
            }
            return ProjectItemImpl.newManagedItem(repositoryItem, library, this);

        } catch (VersionSchemeException e) {
            throw new RepositoryException(e.getMessage(), e);
        }
    }

    /**
     * For <code>TLLibrary</code> instances, this method will return the name of the default
     * filename for the library (per the library's version scheme). For any non-OTM libraries, this
     * method simply returns the name of the library file without any path-specific information.
     * 
     * @param library
     *            the library whose filename is to be returned
     * @return String
     */
    private String getPublicationFilename(AbstractLibrary library) {
        String filename = null;

        if (library instanceof TLLibrary) {
            try {
                TLLibrary otmLibrary = (TLLibrary) library;
                VersionScheme vScheme = VersionSchemeFactory.getInstance().getVersionScheme(
                        otmLibrary.getVersionScheme());

                filename = vScheme.getDefaultFileHint(otmLibrary.getNamespace(),
                        otmLibrary.getName());

            } catch (VersionSchemeException e) {
                // No action - Return the filename from the library's URL
            }
        }
        if ((filename == null) && (library != null)) {
            filename = getLibraryUrlFilename(library);
        }
        return filename;
    }

    /**
     * Returns the name of the file content for the given library without any path-specific
     * information.
     * 
     * @param library
     *            the library whose filename is to be returned
     * @return String
     */
    private String getLibraryUrlFilename(AbstractLibrary library) {
        String filepath = library.getLibraryUrl().getFile();
        int lastPathBreak = filepath.lastIndexOf('/');
        String filename;

        if (lastPathBreak < 0) {
            filename = filepath;

        } else if (lastPathBreak < filepath.length()) {
            filename = filepath.substring(lastPathBreak + 1);

        } else {
            filename = null; // No filename if the path ends with a '/'
        }
        return filename;
    }

    /**
     * Listener that responds to the addition of <code>TLInclude</code> elements into a library,
     * adding the required project-item dependencies where they are needed.
     * 
     * @author S. Livezey
     */
    private class IncludeDependencyListener implements
            ModelEventListener<OwnershipEvent<TLLibrary, TLInclude>, TLLibrary> {

        /**
         * @see org.opentravel.schemacompiler.event.ModelEventListener#processModelEvent(org.opentravel.schemacompiler.event.ModelEvent)
         */
        @Override
        public void processModelEvent(OwnershipEvent<TLLibrary, TLInclude> event) {
            if (event.getType() == ModelEventType.INCLUDE_ADDED) {
                ProjectItem item = getProjectItem(event.getSource());

                if (item != null) {
                    for (Project project : getAssignedProjects(item)) {
                        try {
                            updateProjectDependencies(project, null);

                        } catch (RepositoryException e) {
                            // Ignore and keep going
                        }
                    }
                }
            }
        }

        /**
         * @see org.opentravel.schemacompiler.event.ModelEventListener#getEventClass()
         */
        @Override
        public Class<?> getEventClass() {
            return OwnershipEvent.class;
        }

        /**
         * @see org.opentravel.schemacompiler.event.ModelEventListener#getSourceObjectClass()
         */
        @Override
        public Class<TLLibrary> getSourceObjectClass() {
            return TLLibrary.class;
        }

    }

    /**
     * Listener that responds to the addition of <code>TLNamespaceImport</code> elements into a
     * library, adding the required project-item dependencies where they are needed.
     * 
     * @author S. Livezey
     */
    private class ImportDependencyListener implements
            ModelEventListener<OwnershipEvent<TLLibrary, TLNamespaceImport>, TLLibrary> {

        /**
         * @see org.opentravel.schemacompiler.event.ModelEventListener#processModelEvent(org.opentravel.schemacompiler.event.ModelEvent)
         */
        @Override
        public void processModelEvent(OwnershipEvent<TLLibrary, TLNamespaceImport> event) {
            if (event.getType() == ModelEventType.IMPORT_ADDED) {
                ProjectItem item = getProjectItem(event.getSource());

                if (item != null) {
                    for (Project project : getAssignedProjects(item)) {
                        try {
                            updateProjectDependencies(project, null);

                        } catch (RepositoryException e) {
                            // Ignore and keep going
                        }
                    }
                }
            }
        }

        /**
         * @see org.opentravel.schemacompiler.event.ModelEventListener#getEventClass()
         */
        @Override
        public Class<?> getEventClass() {
            return OwnershipEvent.class;
        }

        /**
         * @see org.opentravel.schemacompiler.event.ModelEventListener#getSourceObjectClass()
         */
        @Override
        public Class<TLLibrary> getSourceObjectClass() {
            return TLLibrary.class;
        }

    }

}
