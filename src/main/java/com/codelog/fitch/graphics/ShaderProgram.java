/*

A platformer game written using OpenGL.
    Copyright (C) 2017-2018  Jaco Malan

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.

*/

package com.codelog.fitch.graphics;

import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import static com.codelog.fitch.Tools.loadFile;

public class ShaderProgram {

    private int id;
    private String vspath;
    private String fspath;

    public ShaderProgram(GL4 gl) {
        this.id = gl.glCreateProgram();
    }

    public void compile(GL4 gl) throws ShaderCompilationException, IOException {

        if (vspath.equalsIgnoreCase("") || fspath.equalsIgnoreCase("")) {
            throw new ShaderCompilationException("Missing one or more shaders!");
        }

        int vshader = gl.glCreateShader(gl.GL_VERTEX_SHADER);
        int fshader = gl.glCreateShader(gl.GL_FRAGMENT_SHADER);

        String[] vss = { loadFile(vspath) };
        String[] fss = { loadFile(fspath) };

        gl.glShaderSource(vshader, 1, vss, null);
        gl.glShaderSource(fshader, 1, fss, null);

        gl.glCompileShader(vshader);
        if (getShaderStatus(gl, vshader) == gl.GL_FALSE) {
            throw new ShaderCompilationException(getShaderLog(gl, vshader));
        }

        gl.glCompileShader(fshader);
        if (getShaderStatus(gl, fshader) == gl.GL_FALSE) {
            throw new ShaderCompilationException(getShaderLog(gl, fshader));
        }

        gl.glAttachShader(this.id, vshader);
        gl.glAttachShader(this.id, fshader);

        gl.glLinkProgram(this.id);

        gl.glDeleteShader(vshader);
        gl.glDeleteShader(fshader);

    }

    private static int getShaderStatus(GL4 gl, int id) {

        IntBuffer vstatus = IntBuffer.allocate(1);
        gl.glGetShaderiv(id, gl.GL_COMPILE_STATUS, vstatus);

        return vstatus.get(0);

    }

    private static String getShaderLog(GL4 gl, int id) {

        IntBuffer logSize = IntBuffer.allocate(1);
        gl.glGetShaderiv(id, gl.GL_INFO_LOG_LENGTH, logSize);
        ByteBuffer infoLog = ByteBuffer.allocate(logSize.get(0));
        gl.glGetShaderInfoLog(id, logSize.get(0), null, infoLog);

        byte[] bytes = new byte[logSize.get(0)];
        infoLog.get(bytes);
        return new String(bytes);

    }

    public int getID() { return this.id; }

    public void addVertexShader(String filePath) { vspath = filePath; }
    public void addFragmentShader(String filePath) { fspath = filePath; }

    public void bind(GL4 gl) {
        gl.glUseProgram(this.id);
    }

}
