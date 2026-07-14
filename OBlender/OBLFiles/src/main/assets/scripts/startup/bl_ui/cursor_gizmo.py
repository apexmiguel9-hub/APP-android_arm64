# SPDX-License-Identifier: GPL-2.0-or-later

import bpy
from bpy.types import GizmoGroup


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
        try:
            gz = self.gizmos.new("GIZMO_GT_move_3d")
            gz.target_set_prop("offset", context.scene.cursor, "location")

            gz.color = 0.8, 0.8, 0.8
            gz.alpha = 0.5
            gz.color_highlight = 1.0, 1.0, 1.0
            gz.alpha_highlight = 1.0
            gz.scale_basis = 0.5

            self._gizmo = gz
        except:
            import traceback
            traceback.print_exc()

    def refresh(self, context):
        pass
