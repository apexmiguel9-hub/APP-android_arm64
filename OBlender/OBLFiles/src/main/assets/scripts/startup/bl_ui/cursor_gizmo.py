# SPDX-License-Identifier: GPL-2.0-or-later

import bpy
import os
import sys
from mathutils import Matrix

DEBUG_LOG = "/sdcard/com.epai.oblender/cursor_gizmo.log"


def debug(msg):
    try:
        with open(DEBUG_LOG, "a") as f:
            f.write(msg + "\n")
    except:
        pass
    try:
        print(msg)
        sys.stdout.flush()
    except:
        pass


debug("=== cursor_gizmo.py loaded ===")


class VIEW3D_GT_cursor_indicator_widget(bpy.types.Gizmo):
    bl_idname = "VIEW3D_GT_cursor_indicator_widget"

    def draw(self, context):
        self.draw_preset_circle(self.matrix_basis, axis='POS_Z')


class VIEW3D_GGT_cursor_indicator(bpy.types.GizmoGroup):
    bl_idname = "VIEW3D_GGT_cursor_indicator"
    bl_label = "Cursor Indicator"
    bl_space_type = 'VIEW_3D'
    bl_region_type = 'WINDOW'
    bl_options = {'3D', 'PERSISTENT'}

    @classmethod
    def poll(cls, context):
        debug("poll: mode=%s" % context.mode)
        try:
            if context.mode not in {'PAINT_GPENCIL', 'SCULPT_GPENCIL'}:
                debug("poll: wrong mode")
                return False
            space = context.space_data
            if space is None or space.type != 'VIEW_3D':
                debug("poll: no view3d")
                return False
            tool = context.workspace.tools.from_space_view3d_mode(context.mode, create=False)
            if tool is None:
                debug("poll: no tool")
                return False
            debug("poll: tool.idname=%s" % tool.idname)
            return tool.idname == "builtin.cursor"
        except Exception as e:
            debug("poll ERROR: %s" % str(e))
            return False

    def setup(self, context):
        debug("setup called")
        try:
            gizmo = self.gizmos.new(VIEW3D_GT_cursor_indicator_widget.bl_idname)
            gizmo.color = 0.8, 0.8, 0.2
            gizmo.alpha = 0.6
            gizmo.color_highlight = 1.0, 1.0, 0.5
            gizmo.alpha_highlight = 1.0
            gizmo.scale_basis = 0.3
            self._indicator = gizmo
            debug("setup: gizmo created OK")
        except Exception as e:
            debug("setup ERROR: %s" % str(e))

    def draw_prepare(self, context):
        try:
            cursor = context.scene.cursor.location
            self._indicator.matrix_basis = Matrix.Translation(cursor)
        except Exception as e:
            pass


classes = (
    VIEW3D_GT_cursor_indicator_widget,
    VIEW3D_GGT_cursor_indicator,
)

debug("classes registered")
