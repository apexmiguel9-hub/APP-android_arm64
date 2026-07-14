# SPDX-License-Identifier: GPL-2.0-or-later

import bpy
from bpy.types import Gizmo, GizmoGroup
from mathutils import Matrix, Vector
from bpy_extras.view3d_utils import region_2d_to_location_3d


AXIS_VECS = {
    'X': Vector((1, 0, 0)),
    'Y': Vector((0, 1, 0)),
    'Z': Vector((0, 0, 1)),
}

AXIS_COLORS = {
    'X': (1.0, 0.2, 0.2),
    'Y': (0.2, 1.0, 0.2),
    'Z': (0.2, 0.2, 1.0),
}


def _make_arrow_verts():
    s = 0.06
    t = 0.18
    return (
        (-s, -s, 0.0), (-s, s, 0.0), (-s, -s, 2.0),
        (-s, s, 0.0), (-s, s, 2.0), (-s, -s, 2.0),
        (s, -s, 0.0), (s, s, 0.0), (s, -s, 2.0),
        (s, s, 0.0), (s, s, 2.0), (s, -s, 2.0),
        (-s, -s, 0.0), (s, -s, 0.0), (-s, -s, 2.0),
        (s, -s, 0.0), (s, -s, 2.0), (-s, -s, 2.0),
        (-s, s, 0.0), (s, s, 0.0), (-s, s, 2.0),
        (s, s, 0.0), (s, s, 2.0), (-s, s, 2.0),
        (0.0, 0.0, 3.0), (-t, -t, 2.0), (t, -t, 2.0),
        (0.0, 0.0, 3.0), (t, -t, 2.0), (t, t, 2.0),
        (0.0, 0.0, 3.0), (t, t, 2.0), (-t, t, 2.0),
        (0.0, 0.0, 3.0), (-t, t, 2.0), (-t, -t, 2.0),
    )


_ARROW_VERTS = _make_arrow_verts()


class VIEW3D_GT_cursor_axis_arrow(Gizmo):
    bl_idname = "VIEW3D_GT_cursor_axis_arrow"

    __slots__ = (
        "custom_shape",
        "init_mouse",
        "init_loc",
        "axis_name",
    )

    def setup(self):
        if not hasattr(self, "custom_shape"):
            self.custom_shape = self.new_custom_shape('TRIS', _ARROW_VERTS)

    def draw(self, context):
        self.draw_custom_shape(self.custom_shape)

    def draw_select(self, context, select_id):
        self.draw_custom_shape(self.custom_shape, select_id=select_id)

    def invoke(self, context, event):
        self.init_mouse = Vector((event.mouse_x, event.mouse_y))
        self.init_loc = context.scene.cursor.location.copy()
        return {'RUNNING_MODAL'}

    def exit(self, context, cancel):
        if cancel:
            context.scene.cursor.location = self.init_loc

    def modal(self, context, event, tweak):
        region = context.region
        rv3d = context.space_data.region_3d

        mouse_3d = region_2d_to_location_3d(
            region, rv3d,
            (event.mouse_x, event.mouse_y),
            self.init_loc,
        )
        init_mouse_3d = region_2d_to_location_3d(
            region, rv3d,
            self.init_mouse,
            self.init_loc,
        )

        delta = mouse_3d - init_mouse_3d
        axis = AXIS_VECS[self.axis_name]
        proj = axis * delta.dot(axis)
        context.scene.cursor.location = self.init_loc + proj
        return {'RUNNING_MODAL'}


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
        self._arrows = []
        for axis in ('X', 'Y', 'Z'):
            gz = self.gizmos.new(VIEW3D_GT_cursor_axis_arrow.bl_idname)
            gz.axis_name = axis
            col = AXIS_COLORS[axis]
            gz.color = col
            gz.alpha = 0.6
            gz.color_highlight = col
            gz.alpha_highlight = 1.0
            gz.scale_basis = 0.4
            gz.use_draw_modal = True
            self._arrows.append(gz)

    def draw_prepare(self, context):
        loc = context.scene.cursor.location
        for gz in self._arrows:
            mat = Matrix.Translation(loc)
            if gz.axis_name == 'X':
                mat @= Matrix.Rotation(-1.5708, 4, 'Y')
            elif gz.axis_name == 'Y':
                mat @= Matrix.Rotation(1.5708, 4, 'X')
            gz.matrix_basis = mat


classes = (
    VIEW3D_GT_cursor_axis_arrow,
    VIEW3D_GGT_cursor_indicator,
)
