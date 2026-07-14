# SPDX-License-Identifier: GPL-2.0-or-later

import bpy
from bpy.types import Gizmo, GizmoGroup
from mathutils import Matrix


class VIEW3D_GT_cursor_widget(bpy.types.Gizmo):
    bl_idname = "VIEW3D_GT_cursor_widget"

    def draw(self, context):
        self.draw_preset_circle(self.matrix_basis, axis='POS_Z')


class VIEW3D_GGT_cursor_indicator(GizmoGroup):
    bl_idname = "VIEW3D_GGT_cursor_indicator"
    bl_label = "Cursor Translate"
    bl_space_type = 'VIEW_3D'
    bl_region_type = 'WINDOW'
    bl_options = {'3D', 'PERSISTENT'}

    @classmethod
    def poll(cls, context):
        if context.mode not in {'PAINT_GPENCIL', 'SCULPT_GPENCIL'}:
            return False
        space = context.space_data
        if space is None or space.type != 'VIEW_3D':
            return False
        tool = context.workspace.tools.from_space_view3d_mode(context.mode, create=False)
        if tool is None:
            return False
        return tool.idname == "builtin.cursor"

    def setup(self, context):
        gz = self.gizmos.new(VIEW3D_GT_cursor_widget.bl_idname)
        gz.color = 0.8, 0.8, 0.2
        gz.alpha = 0.6
        gz.color_highlight = 1.0, 1.0, 0.5
        gz.alpha_highlight = 1.0
        gz.scale_basis = 0.3
        self._indicator = gz

    def draw_prepare(self, context):
        self._indicator.matrix_basis = Matrix.Translation(context.scene.cursor.location)


classes = (
    VIEW3D_GT_cursor_widget,
    VIEW3D_GGT_cursor_indicator,
)
