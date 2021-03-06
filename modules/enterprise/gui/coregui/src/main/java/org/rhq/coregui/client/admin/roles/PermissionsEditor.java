/*
 * RHQ Management Platform
 * Copyright (C) 2005-2012 Red Hat, Inc.
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package org.rhq.coregui.client.admin.roles;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import com.google.gwt.core.client.JavaScriptObject;
import com.smartgwt.client.core.RefDataClass;
import com.smartgwt.client.data.Record;
import com.smartgwt.client.types.Alignment;
import com.smartgwt.client.types.Autofit;
import com.smartgwt.client.types.ListGridEditEvent;
import com.smartgwt.client.types.ListGridFieldType;
import com.smartgwt.client.util.JSOHelper;
import com.smartgwt.client.widgets.Label;
import com.smartgwt.client.widgets.form.fields.CheckboxItem;
import com.smartgwt.client.widgets.grid.ListGrid;
import com.smartgwt.client.widgets.grid.ListGridField;
import com.smartgwt.client.widgets.grid.ListGridRecord;
import com.smartgwt.client.widgets.grid.events.ChangedEvent;
import com.smartgwt.client.widgets.grid.events.ChangedHandler;
import com.smartgwt.client.widgets.grid.events.RecordClickEvent;
import com.smartgwt.client.widgets.grid.events.RecordClickHandler;
import com.smartgwt.client.widgets.layout.VLayout;

import org.rhq.core.domain.authz.Permission;
import org.rhq.core.domain.authz.Role;
import org.rhq.core.domain.common.ProductInfo;
import org.rhq.coregui.client.CoreGUI;
import org.rhq.coregui.client.Messages;
import org.rhq.coregui.client.util.enhanced.EnhancedVStack;
import org.rhq.coregui.client.util.message.Message;

/**
 * An editor for editing the set of RHQ {@link Permission permission}s associated with an RHQ {@link Role role}.
 *
 * @author Ian Springer
 */
public class PermissionsEditor extends EnhancedVStack {

    private static Messages MSG = CoreGUI.getMessages();

    private ListGrid globalPermissionsGrid;
    private ListGrid resourcePermissionsGrid;
    private ListGrid bundleGroupPermissionsGrid;
    private Set<Permission> selectedPermissions;
    private RoleEditView roleEditView;
    private boolean isReadOnly;
    private Object originalValue;

    public PermissionsEditor(RoleEditView roleEditView, boolean isReadOnly) {
        super();

        this.roleEditView = roleEditView;
        this.isReadOnly = isReadOnly;
        // Default permission set
        this.selectedPermissions = EnumSet.of(Permission.VIEW_RESOURCE, Permission.VIEW_BUNDLES_IN_GROUP);

        setWidth("95%");
        setHeight100();

        VLayout spacer = createVerticalSpacer(13);
        addMember(spacer);

        Label globalPermissionsHeader = new Label("<h4>" + MSG.view_adminRoles_permissions_globalPermissions()
            + "</h4>");
        globalPermissionsHeader.setHeight(17);
        addMember(globalPermissionsHeader);

        this.globalPermissionsGrid = createGlobalPermissionsGrid();
        addMember(this.globalPermissionsGrid);

        spacer = createVerticalSpacer(13);
        addMember(spacer);

        Label resourcePermissionsHeader = new Label("<h4>" + MSG.view_adminRoles_permissions_resourcePermissions()
            + "</h4>");
        resourcePermissionsHeader.setHeight(17);
        addMember(resourcePermissionsHeader);

        this.resourcePermissionsGrid = createResourcePermissionsGrid();
        addMember(this.resourcePermissionsGrid);

        addMember(spacer);

        Label bundleGroupPermissionsHeader = new Label("<h4>" + MSG.view_adminRoles_permissions_bundlePermissions()
            + "</h4>");
        bundleGroupPermissionsHeader.setHeight(17);
        addMember(bundleGroupPermissionsHeader);

        this.bundleGroupPermissionsGrid = createBundleGroupPermissionsGrid();
        addMember(this.bundleGroupPermissionsGrid);

    }

    public void reset() {
        //setValue(this.originalValue);
        redraw();
    }

    @Override
    public void redraw() {
        this.selectedPermissions = getValueAsPermissionSet();

        // Update the value of the authorized fields in each row of the grids.

        ListGridRecord[] globalPermissionRecords = this.globalPermissionsGrid.getRecords();
        for (ListGridRecord record : globalPermissionRecords) {
            String permissionName = record.getAttribute("name");
            Permission permission = Permission.valueOf(permissionName);
            record.setAttribute("authorized", this.selectedPermissions.contains(permission));
        }

        ListGridRecord[] resourcePermissionRecords = this.resourcePermissionsGrid.getRecords();
        for (ListGridRecord record : resourcePermissionRecords) {
            String readPermissionName = record.getAttribute("readName");
            Permission readPermission = Permission.valueOf(readPermissionName);
            record.setAttribute("readAuthorized", this.selectedPermissions.contains(readPermission));

            String writePermissionName = record.getAttribute("writeName");
            Permission writePermission = Permission.valueOf(writePermissionName);
            record.setAttribute("writeAuthorized", this.selectedPermissions.contains(writePermission));
        }

        ListGridRecord[] bundleGroupPermissionRecords = this.bundleGroupPermissionsGrid.getRecords();
        for (ListGridRecord record : bundleGroupPermissionRecords) {
            String permissionName = record.getAttribute("name");
            Permission permission = Permission.valueOf(permissionName);
            record.setAttribute("authorized", this.selectedPermissions.contains(permission));
        }

        markForRedraw();
    }

    private Set<Permission> getValueAsPermissionSet() {
        Object nativeArray = this.roleEditView.getForm().getValue(RolesDataSource.Field.PERMISSIONS);
        if (this.originalValue == null) {
            this.originalValue = nativeArray;
        }
        ListGridRecord[] permissionRecords = convertToListGridRecordArray((JavaScriptObject) nativeArray);
        return RolesDataSource.toPermissionSet(permissionRecords);
    }

    private ListGrid createGlobalPermissionsGrid() {
        ProductInfo productInfo = CoreGUI.get().getProductInfo();

        ListGrid grid = createPermissionsGrid();

        // TODO: Add table title.

        ListGridField iconField = createIconField();

        ListGridField displayNameField = new ListGridField("displayName", MSG.common_title_name(), 130);

        ListGridField descriptionField = new ListGridField("description", MSG.common_title_description());
        descriptionField.setWrap(true);

        final ListGridField authorizedField = createAuthorizedField("authorized",
            MSG.view_adminRoles_permissions_isAuthorized(), "name", grid, false);

        grid.setFields(iconField, displayNameField, authorizedField, descriptionField);

        List<ListGridRecord> records = new ArrayList<ListGridRecord>();
        ListGridRecord record = createPermissionRecord(MSG.view_adminRoles_permissions_perm_manageSecurity(),
            "global/Locked", Permission.MANAGE_SECURITY, MSG.view_adminRoles_permissions_permDesc_manageSecurity());
        records.add(record);

        record = createPermissionRecord(MSG.view_adminRoles_permissions_perm_manageInventory(),
            "subsystems/inventory/Inventory", Permission.MANAGE_INVENTORY,
            MSG.view_adminRoles_permissions_permDesc_manageInventory());
        records.add(record);

        record = createPermissionRecord(MSG.view_adminRoles_permissions_perm_manageSettings(),
            "subsystems/configure/Configure", Permission.MANAGE_SETTINGS,
            MSG.view_adminRoles_permissions_permDesc_manageSettings(productInfo.getShortName()));
        records.add(record);

        record = createPermissionRecord(MSG.view_adminRoles_permissions_perm_manageRepositories(),
            "subsystems/content/Content", Permission.MANAGE_REPOSITORIES,
            MSG.view_adminRoles_permissions_permDesc_manageRepositories());
        records.add(record);

        record = createPermissionRecord(MSG.view_adminRoles_permissions_perm_manageBundles(),
            "subsystems/bundle/Bundle", Permission.MANAGE_BUNDLE,
            MSG.view_adminRoles_permissions_permDesc_manageBundles());
        records.add(record);

        record = createPermissionRecord(MSG.view_adminRoles_permissions_perm_manageBundleGroups(),
            "subsystems/bundle/BundleGroup", Permission.MANAGE_BUNDLE_GROUPS,
            MSG.view_adminRoles_permissions_permDesc_manageBundleGroups());
        records.add(record);

        record = createPermissionRecord(MSG.view_adminRoles_permissions_perm_viewUsers(), "global/User",
            Permission.VIEW_USERS, MSG.view_adminRoles_permissions_permDesc_viewUsers());
        records.add(record);

        grid.setData(records.toArray(new ListGridRecord[records.size()]));

        return grid;
    }

    private ListGrid createResourcePermissionsGrid() {
        ListGrid grid = createPermissionsGrid();
        // TODO: Add table title.

        ListGridField iconField = createIconField();

        ListGridField displayNameField = new ListGridField("displayName", MSG.common_title_name(), 130);

        ListGridField descriptionField = new ListGridField("description", MSG.common_title_description());
        descriptionField.setWrap(true);

        ListGridField readField = createAuthorizedField("readAuthorized", MSG.view_adminRoles_permissions_isRead(),
            "readName", grid, true);
        ListGridField writeField = createAuthorizedField("writeAuthorized", MSG.view_adminRoles_permissions_isWrite(),
            "writeName", grid, false);

        grid.setFields(iconField, displayNameField, readField, writeField, descriptionField);

        List<ListGridRecord> records = new ArrayList<ListGridRecord>();

        ListGridRecord record = createResourcePermissionRecord(MSG.view_adminRoles_permissions_perm_inventory(),
            "subsystems/inventory/Inventory", Permission.VIEW_RESOURCE,
            MSG.view_adminRoles_permissions_permReadDesc_inventory(), Permission.MODIFY_RESOURCE,
            MSG.view_adminRoles_permissions_permWriteDesc_inventory());
        records.add(record);

        record = createResourcePermissionRecord(MSG.view_adminRoles_permissions_perm_manageMeasurements(),
            "subsystems/monitor/Monitor", Permission.VIEW_RESOURCE,
            MSG.view_adminRoles_permissions_permReadDesc_manageMeasurements(), Permission.MANAGE_MEASUREMENTS,
            MSG.view_adminRoles_permissions_permWriteDesc_manageMeasurements());
        records.add(record);

        record = createResourcePermissionRecord(MSG.view_adminRoles_permissions_perm_manageAlerts(),
            "subsystems/alert/Alerts", Permission.VIEW_RESOURCE,
            MSG.view_adminRoles_permissions_permReadDesc_manageAlerts(), Permission.MANAGE_ALERTS,
            MSG.view_adminRoles_permissions_permWriteDesc_manageAlerts());
        records.add(record);

        record = createResourcePermissionRecord(MSG.view_adminRoles_permissions_perm_configure(),
            "subsystems/configure/Configure", Permission.CONFIGURE_READ,
            MSG.view_adminRoles_permissions_permReadDesc_configure(), Permission.CONFIGURE_WRITE,
            MSG.view_adminRoles_permissions_permWriteDesc_configure());
        records.add(record);

        record = createResourcePermissionRecord(MSG.view_adminRoles_permissions_perm_control(),
            "subsystems/control/Operation", Permission.VIEW_RESOURCE,
            MSG.view_adminRoles_permissions_permReadDesc_control(), Permission.CONTROL,
            MSG.view_adminRoles_permissions_permWriteDesc_control());
        records.add(record);

        record = createResourcePermissionRecord(MSG.view_adminRoles_permissions_perm_manageEvents(),
            "subsystems/event/Events", Permission.VIEW_RESOURCE,
            MSG.view_adminRoles_permissions_permReadDesc_manageEvents(), Permission.MANAGE_EVENTS,
            MSG.view_adminRoles_permissions_permWriteDesc_manageEvents());
        records.add(record);

        record = createResourcePermissionRecord(MSG.view_adminRoles_permissions_perm_manageContent(),
            "subsystems/content/Content", Permission.VIEW_RESOURCE,
            MSG.view_adminRoles_permissions_permReadDesc_manageContent(), Permission.MANAGE_CONTENT,
            MSG.view_adminRoles_permissions_permWriteDesc_manageContent());
        records.add(record);

        record = createResourcePermissionRecord(MSG.view_adminRoles_permissions_perm_createChildResources(),
            "subsystems/inventory/CreateChild", Permission.VIEW_RESOURCE,
            MSG.view_adminRoles_permissions_permReadDesc_createChildResources(), Permission.CREATE_CHILD_RESOURCES,
            MSG.view_adminRoles_permissions_permWriteDesc_createChildResources());
        records.add(record);

        record = createResourcePermissionRecord(MSG.view_adminRoles_permissions_perm_deleteChildResources(),
            "subsystems/inventory/DeleteChild", Permission.VIEW_RESOURCE,
            MSG.view_adminRoles_permissions_permReadDesc_deleteChildResources(), Permission.DELETE_RESOURCE,
            MSG.view_adminRoles_permissions_permWriteDesc_deleteChildResources());
        records.add(record);

        record = createResourcePermissionRecord(MSG.view_adminRoles_permissions_perm_manageDrift(),
            "subsystems/drift/Drift", Permission.VIEW_RESOURCE,
            MSG.view_adminRoles_permissions_permReadDesc_manageDrift(), Permission.MANAGE_DRIFT,
            MSG.view_adminRoles_permissions_permWriteDesc_manageDrift());
        records.add(record);

        grid.setData(records.toArray(new ListGridRecord[records.size()]));

        return grid;
    }

    private ListGrid createBundleGroupPermissionsGrid() {
        ListGrid grid = createPermissionsGrid();
        // TODO: Add table title.

        ListGridField iconField = createIconField();

        ListGridField displayNameField = new ListGridField("displayName", MSG.common_title_name(), 130);

        ListGridField descriptionField = new ListGridField("description", MSG.common_title_description());
        descriptionField.setWrap(true);

        final ListGridField authorizedField = createAuthorizedField("authorized",
            MSG.view_adminRoles_permissions_isAuthorized(), "name", grid, false);

        grid.setFields(iconField, displayNameField, authorizedField, descriptionField);

        List<ListGridRecord> records = new ArrayList<ListGridRecord>();

        ListGridRecord record = createPermissionRecord(MSG.view_adminRoles_permissions_perm_createBundles(),
            "subsystems/content/Content", Permission.CREATE_BUNDLES,
            MSG.view_adminRoles_permissions_permDesc_createBundles());
        records.add(record);

        record = createPermissionRecord(MSG.view_adminRoles_permissions_perm_deleteBundles(),
            "subsystems/content/Content", Permission.DELETE_BUNDLES,
            MSG.view_adminRoles_permissions_permDesc_deleteBundles());
        records.add(record);

        record = createPermissionRecord(MSG.view_adminRoles_permissions_perm_viewBundles(),
            "subsystems/content/Content", Permission.VIEW_BUNDLES,
            MSG.view_adminRoles_permissions_permDesc_viewBundles());
        records.add(record);

        record = createPermissionRecord(MSG.view_adminRoles_permissions_perm_deployBundles(),
            "subsystems/content/Content", Permission.DEPLOY_BUNDLES,
            MSG.view_adminRoles_permissions_permDesc_deployBundles());
        records.add(record);

        record = createPermissionRecord(MSG.view_adminRoles_permissions_perm_assignBundlesToGroup(),
            "subsystems/bundle/BundleGroup", Permission.ASSIGN_BUNDLES_TO_GROUP,
            MSG.view_adminRoles_permissions_permDesc_assignBundlesToGroup());
        records.add(record);

        record = createPermissionRecord(MSG.view_adminRoles_permissions_perm_unassignBundlesFromGroup(),
            "subsystems/bundle/BundleGroup", Permission.UNASSIGN_BUNDLES_FROM_GROUP,
            MSG.view_adminRoles_permissions_permDesc_unassignBundlesFromGroup());
        records.add(record);

        record = createPermissionRecord(MSG.view_adminRoles_permissions_perm_createBundlesInGroup(),
            "subsystems/bundle/BundleGroup", Permission.CREATE_BUNDLES_IN_GROUP,
            MSG.view_adminRoles_permissions_permDesc_createBundlesInGroup());
        records.add(record);

        record = createPermissionRecord(MSG.view_adminRoles_permissions_perm_deleteBundlesFromGroup(),
            "subsystems/bundle/BundleGroup", Permission.DELETE_BUNDLES_FROM_GROUP,
            MSG.view_adminRoles_permissions_permDesc_deleteBundlesFromGroup());
        records.add(record);

        record = createPermissionRecord(MSG.view_adminRoles_permissions_perm_deployBundlesToGroup(),
            "subsystems/bundle/BundleGroup", Permission.DEPLOY_BUNDLES_TO_GROUP,
            MSG.view_adminRoles_permissions_permDesc_deployBundlesToGroup());
        records.add(record);

        grid.setData(records.toArray(new ListGridRecord[records.size()]));

        return grid;
    }

    private ListGridField createIconField() {
        ListGridField iconField = new ListGridField("icon", "&nbsp;", 28);
        iconField.setShowDefaultContextMenu(false);
        iconField.setCanSort(false);
        iconField.setAlign(Alignment.CENTER);
        iconField.setType(ListGridFieldType.IMAGE);
        iconField.setImageURLSuffix("_16.png");
        iconField.setImageWidth(16);
        iconField.setImageHeight(16);
        return iconField;
    }

    private ListGrid createPermissionsGrid() {
        ListGrid grid = new ListGrid();

        grid.setAutoFitData(Autofit.BOTH);
        grid.setWrapCells(true);
        grid.setFixedRecordHeights(false);

        return grid;
    }

    private ListGridField createAuthorizedField(String name, String title, final String nameField, final ListGrid grid,
        boolean readOnlyColumn) {
        final ListGridField authorizedField = new ListGridField(name, title, 65);

        // Show images rather than true/false.
        authorizedField.setType(ListGridFieldType.IMAGE);
        authorizedField.setImageSize(11);

        LinkedHashMap<String, String> valueMap = new LinkedHashMap<String, String>(2);
        // set the proper images different for read-only column
        if (readOnlyColumn) {
            valueMap.put(Boolean.TRUE.toString(), "global/permission_checked_disabled_11.png");
            valueMap.put(Boolean.FALSE.toString(), "global/permission_disabled_11.png");
        } else {
            valueMap.put(Boolean.TRUE.toString(), "global/permission_enabled_11.png");
            valueMap.put(Boolean.FALSE.toString(), "global/permission_disabled_11.png");
        }
        authorizedField.setValueMap(valueMap);
        authorizedField.setCanEdit(true);

        CheckboxItem editor = new CheckboxItem();
        authorizedField.setEditorType(editor);

        if (!this.isReadOnly) {
            grid.setEditEvent(ListGridEditEvent.CLICK);
            final Record[] recordBeingEdited = { null };
            authorizedField.addRecordClickHandler(new RecordClickHandler() {
                public void onRecordClick(RecordClickEvent event) {
                    recordBeingEdited[0] = event.getRecord();
                }
            });
            authorizedField.addChangedHandler(new ChangedHandler() {
                public void onChanged(ChangedEvent event) {
                    Boolean authorized = (Boolean) event.getValue();
                    int recordNum = event.getRowNum();
                    ListGridRecord record = grid.getRecord(recordNum);
                    String permissionName = record.getAttribute(nameField);
                    Permission permission = Permission.valueOf(permissionName);
                    String permissionDisplayName = record.getAttribute("displayName");

                    if (permission == Permission.VIEW_RESOURCE) {
                        String messageString = MSG.view_adminRoles_permissions_readAccessImplied(permissionDisplayName);
                        handleIllegalPermissionSelection(event, messageString);
                    } else if (!authorized && selectedPermissions.contains(Permission.MANAGE_SECURITY)
                        && permission != Permission.MANAGE_SECURITY) {
                        String messageString = MSG
                            .view_adminRoles_permissions_illegalDeselectionDueToManageSecuritySelection(permissionDisplayName);
                        handleIllegalPermissionSelection(event, messageString);
                    } else if (!authorized && selectedPermissions.contains(Permission.MANAGE_INVENTORY)
                        && permission.getTarget() == Permission.Target.RESOURCE) {
                        String messageString = MSG
                            .view_adminRoles_permissions_illegalDeselectionDueToManageInventorySelection(permissionDisplayName);
                        handleIllegalPermissionSelection(event, messageString);
                    } else if (!authorized && selectedPermissions.contains(Permission.CONFIGURE_WRITE)
                        && permission == Permission.CONFIGURE_READ) {
                        String messageString = MSG
                            .view_adminRoles_permissions_illegalDeselectionDueToCorrespondingWritePermSelection(permissionDisplayName);
                        handleIllegalPermissionSelection(event, messageString);
                    } else if (!authorized && selectedPermissions.contains(Permission.MANAGE_BUNDLE)
                        && permission != Permission.MANAGE_BUNDLE && Permission.BUNDLE_ALL.contains(permission)) {
                        String messageString = MSG
                            .view_adminRoles_permissions_illegalDeselectionDueToManageBundleSelection(permissionDisplayName);
                        handleIllegalPermissionSelection(event, messageString);
                    } else if (!authorized && selectedPermissions.contains(Permission.MANAGE_BUNDLE_GROUPS)
                        && permission == Permission.VIEW_BUNDLES) {
                        String messageString = MSG
                            .view_adminRoles_permissions_illegalDeselectionDueToManageBundleGroupsSelection(permissionDisplayName);
                        handleIllegalPermissionSelection(event, messageString);
                    } else {
                        updatePermissions(authorized, permission);

                        // Let our parent role editor know the permissions have been changed, so it can update the
                        // enablement of its Save and Reset buttons.
                        PermissionsEditor.this.roleEditView.onItemChanged();
                    }
                }
            });
        }

        return authorizedField;
    }

    private static void handleIllegalPermissionSelection(ChangedEvent event, String messageString) {
        event.getItem().setValue(true);
        Message message = new Message(messageString, Message.Severity.Warning, EnumSet.of(Message.Option.Transient));
        CoreGUI.getMessageCenter().notify(message);
    }

    private void updatePermissions(Boolean authorized, Permission permission) {
        String messageString = null;
        boolean redrawRequired = false;
        if (authorized) {
            this.selectedPermissions.add(permission);
            if (permission == Permission.MANAGE_SECURITY) {
                // MANAGE_SECURITY implies all other perms.
                if (this.selectedPermissions.addAll(EnumSet.allOf(Permission.class))) {
                    messageString = MSG.view_adminRoles_permissions_autoselecting_manageSecurity_implied();
                    redrawRequired = true;
                }
            } else if (permission == Permission.MANAGE_INVENTORY) {
                // MANAGE_INVENTORY implies all Resource perms.
                if (this.selectedPermissions.addAll(Permission.RESOURCE_ALL)) {
                    messageString = MSG.view_adminRoles_permissions_autoselecting_manageInventory_implied();
                    redrawRequired = true;
                }
            } else if (permission == Permission.CONFIGURE_WRITE) {
                // CONFIGURE_WRITE implies CONFIGURE_READ.
                if (this.selectedPermissions.add(Permission.CONFIGURE_READ)) {
                    messageString = MSG.view_adminRoles_permissions_autoselecting_configureWrite_implied();
                    redrawRequired = true;
                }
            } else if (permission == Permission.MANAGE_BUNDLE) {
                // MANAGE_BUNDLE implies all other bundle-related perms
                if (this.selectedPermissions.addAll(Permission.BUNDLE_ALL)) {
                    messageString = MSG.view_adminRoles_permissions_autoselecting_manageBundle_implied();
                    redrawRequired = true;
                }
            } else if (permission == Permission.MANAGE_BUNDLE_GROUPS) {
                // MANAGE_BUNDLE_GROUPS implies VIEW_BUNDLES
                if (this.selectedPermissions.add(Permission.VIEW_BUNDLES)) {
                    messageString = MSG.view_adminRoles_permissions_autoselecting_manageBundleGroups_implied();
                    redrawRequired = true;
                }
            }
        } else {
            this.selectedPermissions.remove(permission);
        }

        ListGridRecord[] permissionRecords = RolesDataSource.toRecordArray(this.selectedPermissions);
        this.roleEditView.getForm().setValue(RolesDataSource.Field.PERMISSIONS, permissionRecords);

        if (redrawRequired) {
            redraw();
        }

        if (messageString != null) {
            Message message = new Message(messageString, EnumSet.of(Message.Option.Transient));
            CoreGUI.getMessageCenter().notify(message);
        }
    }

    private ListGridRecord createPermissionRecord(String displayName, String icon, Permission permission,
        String description) {
        ListGridRecord record = new ListGridRecord();
        record.setAttribute("displayName", displayName);
        record.setAttribute("icon", icon);
        record.setAttribute("name", permission.name());
        record.setAttribute("description", description);
        record.setAttribute("authorized", this.selectedPermissions.contains(permission));

        return record;
    }

    private ListGridRecord createResourcePermissionRecord(String displayName, String icon, Permission readPermission,
        String readDescription, Permission writePermission, String writeDescription) {
        ListGridRecord record = new ListGridRecord();
        record.setAttribute("displayName", displayName);
        record.setAttribute("icon", icon);
        record.setAttribute("readName", readPermission.name());
        record.setAttribute("readAuthorized", this.selectedPermissions.contains(readPermission));
        record.setAttribute("description", "<b>" + MSG.view_adminRoles_permissions_read() + "</b> " + readDescription
            + "<br/><b>" + MSG.view_adminRoles_permissions_write() + "</b> " + writeDescription);
        record.setAttribute("writeName", writePermission.name());
        record.setAttribute("writeAuthorized", this.selectedPermissions.contains(writePermission));

        return record;
    }

    public Set<Permission> getPermissions() {
        return this.selectedPermissions;
    }

    private static ListGridRecord[] convertToListGridRecordArray(JavaScriptObject jsObject) {
        if (jsObject == null) {
            return new ListGridRecord[0];
        }
        JavaScriptObject[] jsArray = JSOHelper.toArray(jsObject);
        ListGridRecord[] records = new ListGridRecord[jsArray.length];
        for (int i = 0; i < jsArray.length; i++) {
            JavaScriptObject jsArrayItem = jsArray[i];
            ListGridRecord record = (ListGridRecord) RefDataClass.getRef(jsArrayItem);
            if (record == null) {
                record = new ListGridRecord(jsArrayItem);
            }
            records[i] = record;
        }
        return records;
    }

    private VLayout createVerticalSpacer(int height) {
        VLayout spacer = new VLayout();
        spacer.setHeight(height);
        return spacer;
    }

}
