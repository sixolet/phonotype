-- many tomorrows
-- norns study 1

engine.name = "Phonotype"

version_major = 0
version_minor = 1

err_line = ""
script_cache = {nil, nil, nil, nil, nil, nil, nil, nil, nil}
editing_script = 9
edit_col = 1
edit_row = 1
editing = ""

-- This one is just a text editor

BasicModel = {scripts = {}}

function BasicModel:insert_blank(script, idx)
  local s = self.scripts[script]
  if s == nil then
    s = {}
    self.scripts[script] = s
  end
  table.insert(s, idx, "")
end

function BasicModel:add(script, line)
  local s = self.scripts[script]
  if s == nil then
    s = {}
    self.scripts[script] = s
  end
  s[#s + 1] = line
end

function BasicModel:script_size(script)
  if self.scripts[script] == nil then
    return 0
  end
  
  return #(self.scripts[script])
end

function BasicModel:replace(script, idx, line)
  local s = self.scripts[script]
  if s == nil then
    s = {}
    self.scripts[script] = s
  end
  s[idx] = line
end

function BasicModel:remove(script, idx)
  table.remove(self.scripts, idx)
end

function BasicModel:get(script, idx)
  return self.scripts[script][idx]
end

function BasicModel:as_string(script)
  if self.scripts[script] == nil then
    return ""
  end
  
  return table.concat(self.scripts[script], "\n")
end

-- This one is Phonotype

PTModel = {scripts = {}, nonce = 1, outstanding = 0}

function PTModel:get_if_nil(script)
  if self.scripts[script] == nil then
    engine.just_report(self.nonce, script-1)
    self.outstanding = self.nonce
    self.nonce = self.nonce + 1
  end
end

function PTModel:insert_blank(script, idx)
  print("insert_passthrough", self.nonce, script-1, idx-1)
  engine.insert_passthrough(self.nonce, script-1, idx-1)
  self.outstanding = self.nonce
  self.nonce = self.nonce + 1
end

function PTModel:add(script, line)
  print("add", self.nonce, script-1, line)
  engine.add(self.nonce, script-1, line)
  self.outstanding = self.nonce
  self.nonce = self.nonce + 1
end

function PTModel:script_size(script)
  self:get_if_nil(script)
  if self.scripts[script] == nil then
    return 0
  end
  return #(self.scripts[script])
end

function PTModel:replace(script, idx, line)
  print("replace", self.nonce, script-1, idx-1, line)
  engine.replace(self.nonce, script-1, idx-1, line)
  self.outstanding = self.nonce
  self.nonce = self.nonce + 1
end

function PTModel:remove(script, idx)
  print("remove", self.nonce, script-1, idx-1)
  engine.remove(self.nonce, script-1, idx-1)
  self.outstanding = self.nonce
  self.nonce = self.nonce + 1
end

function PTModel:get(script, idx)
  if self.scripts[script] == nil or self.scripts[script][idx] == nil then
    return ""
  end
  return self.scripts[script][idx]
end

function PTModel:as_string(script)
  if self.scripts[script] == nil then
    return ""
  end
  
  return table.concat(self.scripts[script], "\n")
end

model = PTModel

function string.insert(str1, str2, pos)
  if pos == string.len(str1) + 1 then
    return str1..str2
  end
  
  return str1:sub(1,pos-1)..str2..str1:sub(pos)
end

function string.remove(s, pos)
  return s:sub(1,pos-1)..s:sub(pos+1, -1)
end


function script_num_rep(n)
  if n <= 8 then
    return n
  else
    return "M"
  end
end

function init()

  print("the end and the beginning they are the same.")
end

function redraw()
  screen.clear()
  screen.level(16)
  screen.font_face(1)
  screen.font_size(8)
  screen.move(0, 8)
  for i=1,model:script_size(editing_script),1
  do
    screen.move(0, 8*i)
    screen.text(model:get(editing_script, i))
  end
  screen.move(0, 56)
  screen.text(err_line)
  screen.move(0, 8*8-1)
  screen.text(script_num_rep(editing_script))
  screen.move(10, 8*8-1)
  screen.text(editing)
  screen.level(1)
  screen.blend_mode(8)
  local up_to_cursor = string.sub(editing, 1, edit_col-1)
  screen.rect(screen.text_extents(up_to_cursor) + 10, 56, 5, 8)
  screen.fill()
  screen.rect(0, 8*(edit_row-1), 128, 8)
  screen.fill()
  screen.update()
end

function keyboard.char(character)
  if keyboard.alt() or keyboard.ctrl() then
    return
  end
  -- print("ADDING .", character, ".")
  editing = string.insert(editing, character:upper(), edit_col)
  edit_col = edit_col + 1
  redraw()
end

function keyboard.code(key, value)
  -- print("KEY", key)
  -- print("POS", edit_col)
  if value == 1 or value == 2 then -- 1 is down, 2 is held, 0 is release
    if key == "BACKSPACE" then
      if editing:len() == 0 or edit_col == 1 then
        return
      end
      print("attempting to remove", edit_col-1, "from", editing)
      editing = string.remove(editing, edit_col-1)
      edit_col = edit_col - 1
    elseif key == "LEFT" then
      if edit_col <= 1 then
        edit_col = 1
        return
      end
      edit_col = edit_col - 1
    elseif key == "RIGHT" then
      if editing:len() < edit_col then
        edit_col = editing:len() + 1
      else
        edit_col = edit_col + 1
      end
    elseif key == "ENTER" then
      if keyboard.shift() then
        model:insert_blank(editing_script, edit_row)
        editing = model:get(editing_script, edit_row)
      elseif edit_row > model:script_size(editing_script) then
        model:add(editing_script, editing)
      else
        model:replace(editing_script, edit_row, editing)
      end
    elseif key == "UP" then
      if edit_row == 1 then
        return
      end
      edit_row = edit_row - 1
      editing = model:get(editing_script, edit_row)
    elseif key == "DOWN" then
      if edit_row > model:script_size(editing_script) then
        return
      end
      edit_row = edit_row + 1
      editing = model:get(editing_script, edit_row)
    end
  end
  redraw()
end

function osc_in(path, args, from)
  print("osc", path, args[1], args[2], args[3], args[4], from)  
  if path == "/report" then
    local nonce = args[1]
    local script_num = args[2] + 1
    err_line = args[3]
    local script_contents = args[4]

    if PTModel.nonce == nonce then
      PTModel.outstanding = 0
    end
    if script_contents == "" then
      PTModel:insert_blank(args[2], 1)
    end
    
    PTModel.scripts[script_num] = split_lines(script_contents)
    
  end
end

osc.event = osc_in

function split_lines(str)
  local lines = {}
  for s in str:gmatch("[^\r\n]+") do
    table.insert(lines, s)
  end
  return lines
end