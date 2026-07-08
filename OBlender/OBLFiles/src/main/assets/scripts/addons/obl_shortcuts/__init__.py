bl_info = {
    "name": "OBL Mobile Shortcuts",
    "author": "apexmiguel9-hub",
    "version": (1, 2, 0),
    "blender": (3, 6, 0),
    "location": "3D View > Sidebar > OBL",
    "description": "Configurable shortcut grid for Blender on Android",
    "category": "Interface",
}

import bpy
from bpy.types import (
    Operator,
    PropertyGroup,
    AddonPreferences,
    Panel,
)
from bpy.props import (
    StringProperty,
    IntProperty,
    BoolProperty,
    CollectionProperty,
    FloatProperty,
)

DEFAULT_SHORTCUTS = [
    ("Add Cube", "mesh.primitive_cube_add", "MESH_CUBE", "Mesh"),
    ("Add Sphere", "mesh.primitive_uv_sphere_add", "MESH_UVSPHERE", "Mesh"),
    ("Add Cylinder", "mesh.primitive_cylinder_add", "MESH_CYLINDER", "Mesh"),
    ("Add Plane", "mesh.primitive_plane_add", "MESH_PLANE", "Mesh"),
    ("Add Torus", "mesh.primitive_torus_add", "MESH_TORUS", "Mesh"),
    ("Add Monkey", "mesh.primitive_monkey_add", "MESH_MONKEY", "Mesh"),
    ("Delete", "object.delete", "X", "Edit"),
    ("Duplicate", "object.duplicate", "DUPLICATE", "Edit"),
    ("Join", "object.join", "JOIN", "Edit"),
    ("Undo", "ed.undo", "LOOP_BACK", "Edit"),
    ("Redo", "ed.redo", "LOOP_FORWARD", "Edit"),
    ("Extrude", "mesh.extrude_region", "ORIENTATION_NORMAL", "Mesh"),
    ("Loop Cut", "mesh.loopcut_slide", "MOD_SUBSURF", "Mesh"),
    ("Bevel", "mesh.bevel", "MOD_BEVEL", "Mesh"),
    ("Subdivide", "mesh.subdivide", "MOD_SUBSURF", "Mesh"),
    ("Merge", "mesh.merge", "SNAP_MIDPOINT", "Mesh"),
    ("Separate", "mesh.separate", "SEPARATE", "Mesh"),
    ("Smooth", "mesh.vertices_smooth", "MOD_SMOOTH", "Mesh"),
    ("Select All", "mesh.select_all", "SELECT_ALL", "Select"),
    ("Select Inverse", "mesh.select_inverse", "SELECT_INVERT", "Select"),
    ("Box Select", "view3d.select_box", "VIEWZOOM", "Select"),
    ("Circle Select", "view3d.select_circle", "VIEW3D_V3D", "Select"),
    ("Move", "transform.translate", "HANDLETYPE_FREE_VEC", "Transform"),
    ("Rotate", "transform.rotate", "HANDLETYPE_AUTO_U", "Transform"),
    ("Scale", "transform.resize", "HANDLETYPE_AUTO_V", "Transform"),
    ("Snap to Grid", "view3d.snap_selected_to_grid", "SNAP_GRID", "Snap"),
    ("Cursor to Grid", "view3d.snap_cursor_to_grid", "CURSOR", "Snap"),
    ("Cursor to Selected", "view3d.snap_cursor_to_selected", "CURSOR", "Snap"),
    ("Local View", "view3d.localview", "VIEWZOOM", "View"),
    ("Frame Selected", "view3d.view_selected", "VIEW_SELECTED", "View"),
    ("Frame All", "view3d.view_all", "VIEW_ALL", "View"),
    ("X-Ray", "view3d.xray", "XRAY", "View"),
    ("Toggle Wire", "view3d.toggle_shading", "SHADING_WIRE", "View"),
    ("Toggle Solid", "view3d.toggle_shading", "SHADING_SOLID", "View"),
    ("Object Mode", "object.mode_set", "OBJECT_DATAMODE", "Mode"),
    ("Edit Mode", "object.mode_set", "EDITMODE_HLT", "Mode"),
    ("Sculpt Mode", "object.mode_set", "SCULPTMODE_HLT", "Mode"),
]

OP_PARAMS = {
    "object.mode_set": {"mode": "OBJECT"},
    "mesh.select_all": {"action": "SELECT"},
    "mesh.select_inverse": {"action": "INVERT"},
    "view3d.toggle_shading": {"type": "SOLID"},
    "object.subdivision_set": {"level": 1, "relative": False},
}

MODE_MAP = {
    "Object Mode": "OBJECT",
    "Edit Mode": "EDIT",
    "Sculpt Mode": "SCULPT",
}


class OBL_ShortcutItem(PropertyGroup):
    name: StringProperty(name="Name", default="Shortcut")
    idname: StringProperty(name="Operator ID", default="mesh.primitive_cube_add")
    icon: StringProperty(name="Icon", default="MESH_CUBE")
    category: StringProperty(name="Category", default="Mesh")


class OBL_OT_execute_shortcut(Operator):
    bl_idname = "obl.execute_shortcut"
    bl_label = "Execute"

    index: IntProperty(default=0)

    def execute(self, context):
        prefs = context.preferences.addons[__name__].preferences
        if self.index < 0 or self.index >= len(prefs.shortcuts):
            self.report({'ERROR'}, "Invalid shortcut")
            return {'CANCELLED'}

        item = prefs.shortcuts[self.index]
        idname = item.idname
        if not idname or "." not in idname:
            self.report({'ERROR'}, "Invalid operator")
            return {'CANCELLED'}

        parts = idname.split(".")
        mod, name = parts[0], parts[1]

        op_func = getattr(getattr(bpy.ops, mod, None), name, None)
        if op_func is None:
            self.report({'ERROR'}, f"Operator not found: {idname}")
            return {'CANCELLED'}

        params = {}
        if idname == "object.mode_set" and item.name in MODE_MAP:
            params["mode"] = MODE_MAP[item.name]
        elif idname == "view3d.toggle_shading":
            shading_map = {"Toggle Wire": "WIREFRAME", "Toggle Solid": "SOLID"}
            st = shading_map.get(item.name, "SOLID")
            for space in context.area.spaces:
                if space.type == 'VIEW_3D':
                    space.shading.type = st
            return {'FINISHED'}
        elif item.name in OP_PARAMS:
            params = OP_PARAMS[item.name]

        try:
            op_func(**params)
            return {'FINISHED'}
        except Exception as e:
            self.report({'ERROR'}, f"{str(e)[:120]}")
            return {'CANCELLED'}


class OBL_OT_shortcut_grid(Operator):
    bl_idname = "obl.shortcut_grid"
    bl_label = "OBL Shortcuts"

    def invoke(self, context, event):
        return context.window_manager.invoke_props_dialog(self, width=900)

    def draw(self, context):
        prefs = context.preferences.addons[__name__].preferences
        layout = self.layout

        if len(prefs.shortcuts) == 0:
            _init_defaults()

        cats = {}
        for i, s in enumerate(prefs.shortcuts):
            cats.setdefault(s.category or "Other", []).append(i)

        row = layout.row()
        row.label(text=f"OBL Shortcuts ({len(prefs.shortcuts)})", icon='TOOL_SETTINGS')
        row.operator("obl.add_shortcut", text="", icon='ADD')

        layout.separator()

        for cat in sorted(cats):
            items = cats[cat]
            box = layout.box()
            box.label(text=cat, icon='DOT')
            flow = box.grid_flow(row_major=True, columns=3, even_columns=True, even_rows=True)
            for i in items:
                s = prefs.shortcuts[i]
                c = flow.column(align=True)
                c.scale_y = 2.0
                op = c.operator("obl.execute_shortcut", text=s.name)
                op.index = i
                rem = c.operator("obl.remove_shortcut", text="", icon='X', emboss=False)
                rem.index = i


class OBL_OT_add_shortcut(Operator):
    bl_idname = "obl.add_shortcut"
    bl_label = "Add Shortcut"

    def invoke(self, context, event):
        return context.window_manager.invoke_props_dialog(self, width=700)

    def draw(self, context):
        layout = self.layout
        layout.label(text="Select operator to add:", icon='TOOL_SETTINGS')
        layout.separator()

        cats = {}
        for mod_name in sorted(dir(bpy.ops)):
            if mod_name.startswith("_"):
                continue
            mod = getattr(bpy.ops, mod_name, None)
            if mod is None:
                continue
            ops = []
            for op_name in sorted(dir(mod)):
                if op_name.startswith("_"):
                    continue
                ops.append((f"{mod_name}.{op_name}", op_name.replace("_", " ").title()))
            if ops:
                cats[mod_name.title()] = ops

        col = layout.column(align=True)
        for cat in sorted(cats):
            ops = cats[cat]
            box = col.box()
            row = box.row()
            row.label(text=cat, icon='DOT')
            row.label(text=f"{len(ops)}", icon='INFO')
            for op_id, op_display in ops[:15]:
                r = box.row(align=True)
                r.scale_y = 1.2
                op = r.operator("obl.add_shortcut_confirm", text=op_display, icon='ADD')
                op.idname = op_id

    def execute(self, context):
        return {'FINISHED'}


class OBL_OT_add_shortcut_confirm(Operator):
    bl_idname = "obl.add_shortcut_confirm"
    bl_label = "Name Shortcut"

    idname: StringProperty(default="")
    shortcut_name: StringProperty(name="Name", default="")

    def invoke(self, context, event):
        self.shortcut_name = self.idname.split(".")[-1].replace("_", " ").title()
        return context.window_manager.invoke_props_dialog(self, width=400)

    def draw(self, context):
        layout = self.layout
        layout.label(text=f"Operator: {self.idname}")
        layout.separator()
        layout.prop(self, "shortcut_name", text="")

    def execute(self, context):
        name = self.shortcut_name.strip()
        if not name:
            self.report({'ERROR'}, "Name required")
            return {'CANCELLED'}
        prefs = context.preferences.addons[__name__].preferences
        s = prefs.shortcuts.add()
        s.name = name
        s.idname = self.idname
        s.icon = 'TOOL_SETTINGS'
        prefix = self.idname.split(".")[0]
        cat_map = {"mesh": "Mesh", "object": "Object", "transform": "Transform",
                    "view3d": "View", "ed": "Edit", "wm": "Window",
                    "graph": "Animation", "material": "Material"}
        s.category = cat_map.get(prefix, "Custom")
        self.report({'INFO'}, f"Added: {s.name}")
        return {'FINISHED'}


class OBL_OT_remove_shortcut(Operator):
    bl_idname = "obl.remove_shortcut"
    bl_label = "Remove"

    index: IntProperty(default=0)

    def execute(self, context):
        prefs = context.preferences.addons[__name__].preferences
        if 0 <= self.index < len(prefs.shortcuts):
            prefs.shortcuts.remove(self.index)
        return {'FINISHED'}


class OBL_OT_reset_shortcuts(Operator):
    bl_idname = "obl.reset_shortcuts"
    bl_label = "Reset to Defaults"

    def execute(self, context):
        prefs = context.preferences.addons[__name__].preferences
        prefs.shortcuts.clear()
        _init_defaults()
        self.report({'INFO'}, "Reset to defaults")
        return {'FINISHED'}


class OBL_PT_shortcuts_panel(Panel):
    bl_label = "OBL Shortcuts"
    bl_idname = "OBL_PT_shortcuts_panel"
    bl_space_type = 'VIEW_3D'
    bl_region_type = 'UI'
    bl_category = "OBL"

    @classmethod
    def poll(cls, context):
        return bool(context.area)

    def draw(self, context):
        layout = self.layout
        prefs = context.preferences.addons[__name__].preferences

        row = layout.row()
        row.scale_y = 2.0
        row.operator("obl.shortcut_grid", text="Open Grid", icon='TOOL_SETTINGS')
        row.operator("obl.add_shortcut", text="", icon='ADD')

        layout.separator()
        layout.label(text="Quick Access:", icon='DOT')

        if len(prefs.shortcuts) == 0:
            _init_defaults()

        cats = {}
        for i, s in enumerate(prefs.shortcuts):
            cats.setdefault(s.category or "Other", []).append(i)

        for cat in sorted(cats):
            box = layout.box()
            box.label(text=cat, icon='DOT')
            col = box.column(align=True)
            col.scale_y = 1.5
            for i in cats[cat]:
                s = prefs.shortcuts[i]
                r = col.row(align=True)
                op = r.operator("obl.execute_shortcut", text=s.name)
                op.index = i
                rem = r.operator("obl.remove_shortcut", text="", icon='X', emboss=False)
                rem.index = i


class OBL_AddonPreferences(AddonPreferences):
    bl_idname = __name__

    shortcuts: CollectionProperty(type=OBL_ShortcutItem)
    index: IntProperty(default=0)

    def draw(self, context):
        layout = self.layout
        row = layout.row()
        row.operator("obl.reset_shortcuts", text="Reset Defaults")
        row.operator("obl.add_shortcut", text="Add New")


def _init_defaults():
    prefs = bpy.context.preferences.addons[__name__].preferences
    if len(prefs.shortcuts) > 0:
        return
    for name, idname, icon, cat in DEFAULT_SHORTCUTS:
        s = prefs.shortcuts.add()
        s.name = name
        s.idname = idname
        s.icon = icon
        s.category = cat


classes = (
    OBL_ShortcutItem,
    OBL_OT_execute_shortcut,
    OBL_OT_shortcut_grid,
    OBL_OT_add_shortcut,
    OBL_OT_add_shortcut_confirm,
    OBL_OT_remove_shortcut,
    OBL_OT_reset_shortcuts,
    OBL_PT_shortcuts_panel,
    OBL_AddonPreferences,
)

_menus_hooked = ('VIEW3D_MT_view', 'VIEW3D_MT_object_context_menu')


def _menu_func(self, context):
    self.layout.separator()
    self.layout.operator("obl.shortcut_grid", text="OBL Shortcuts", icon='TOOL_SETTINGS')


def register():
    for cls in classes:
        bpy.utils.register_class(cls)
    bpy.app.timers.register(_init_defaults, first_interval=0.5)
    for m in _menus_hooked:
        try:
            getattr(bpy.types, m).append(_menu_func)
        except Exception:
            pass


def unregister():
    for m in _menus_hooked:
        try:
            getattr(bpy.types, m).remove(_menu_func)
        except Exception:
            pass
    for cls in reversed(classes):
        bpy.utils.unregister_class(cls)


if __name__ == "__main__":
    register()
