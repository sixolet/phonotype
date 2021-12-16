-- PHONOTYPE
-- v0.2 @sixolet
-- https://llllllll.co/t/phonotype-code-a-sound/49564
--
-- phonotype exists between
-- keyboard and speaker
--
-- Plug a computer keyboard
-- M is your "main"
-- The last line in M outputs
--
-- Keys:
-- Arrow keys navigate
-- Function keys jump to scripts
-- Esc jumps to scene description
-- In scene description Alt+Enter saves
-- Choose scene from params menu to load
-- Shift+Alt+Esc loads a fresh scene
--
-- Mini tutorial
-- Try
-- SIN 440
-- in M.
--
-- Then on the next
-- line put
-- * IT PERC QN .5
-- (QN = quarter notes.
--  .5 is our env decay)
--
-- Now add
-- + IT * .5 DEL.F IT * .75 M 3
-- DEL.F is a delay w/ feedback
-- we delay by a dotted eigth
-- with a 3s decay.
--
-- Then go back
-- to the first line.
-- Hit ctrl-enter to get
-- A new line above
-- Make it say
-- S+H SCL SIN .1 0 7 QN
-- and change
-- SIN 440
-- to say
-- SIN N.MIN IT

engine.name = "Phonotype"

version_major = 0
version_minor = 1

err_line = ""
script_cache = {nil, nil, nil, nil, nil, nil, nil, nil, nil}
editing_script = 9
edit_col = 1
edit_row = 1
editing = ""
clipboard = ""
moved_line = false --moved scope so it's accessible by model:to_line()
loaded_scene = ""

Deque = require('container/deque')
history = Deque.new()
history_depth = 0

--[[ This one is just a text editor

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

]]--

-- This one is Phonotype

PTModel = {scripts = {}, nonce = 1, outstanding = 0}


function PTModel:trigger_save()
  engine.dump()
end

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

function PTModel:load(script, contents)
  print("load_scene", self.nonce, script-1, contents, type(contents))
  engine.load_scene(self.nonce, script-1, contents)
  -- On loading forget we ever knew scripts contents.
  self.scripts={}
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

function PTModel:super() --simplifies super layer
  return keyboard.alt() or keyboard.ctrl()
end

function PTModel:to_line(line) --moved repeated code here
  edit_row = line
  if not moved_line then
    editing = model:get(editing_script, edit_row)
  end
  edit_col = string.len(editing) + 1
end

function PTModel:push_history(line)
  history:push(line)
  if history:count() > 10 then
    history:pop_back()
  end
end

function PTModel:enter() -- moved here so we can use it when pasting, cutting, etc.
  if keyboard.shift() then
    model:insert_blank(editing_script, edit_row)
    editing = "IT"
    moved_line = true
    model:to_line(edit_row)
  elseif edit_row > model:script_size(editing_script) then
    model:push_history(editing)
    model:add(editing_script, editing)
    model:to_line(edit_row + 1)
  elseif editing == "" then
    model:remove(editing_script, edit_row)
    moved_line = true
    -- Anticipates that the "next" row will become this one, but
    -- we should stay highlighting this
    editing = model:get(editing_script, edit_row+1)
    model:to_line(edit_row)
  else
    model:push_history(editing)
    model:replace(editing_script, edit_row, editing)
    model:to_line(edit_row + 1)
  end
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
  elseif n == 9 then
    return "M"
  elseif n == 10 then
    return "D"
  end
end

function buf_size_format(param)
  local i = param:get()
  if i < 4800 then
    return string.format("%i", i)
  else
    return string.format("%.2f s", i/48000)
  end
end

function init()
  params:add_file("scene", "scene")
  params:set_action("scene", maybe_load_scene)
  params:add_control("gain","gain",controlspec.new(0.1,10,'exp',0,0.5,''))
  params:set_action("gain", function(x) engine.set_param(18, x) end)
  params:add_number("root","root",20,880,440)
  params:set_action("root", function(x) engine.set_param(17, x) end)

  --[[ Doesn't quite work yet
  params:add_taper("defaultfade", "default fade time", 0.01, 30, 1, math.sqrt(math.sqrt(2)), "s")
  params:set_action("defaultfade", function(x) engine.default_fade_time(x) end)
  params:add_taper("defaultquant", "default quant", 1/16, 16, 1/16, 2, "beats")
  params:set_action("defaultquant", function(x) engine.default_quant(x) end)
  ]]--


  local param_spec = controlspec.new(0,1,'lin',1/127.0,0.5,'')
  params:add_group("flexible controls", 16)
  for i=0,15,1 do
    local param_id = string.format("param%i", i)
    params:add_control(
      param_id,
      string.format("param %i", i),
      param_spec)
    params:set_action(param_id, function(x) engine.set_param(i, x) end)
  end

  params:add_group("buffers", 48)
  local buf_size_spec = controlspec.def{
    min=64,
    max=64*48000,
    warp='exp',
    step=256,
    default=8*48000,
    quantum=0.0078125,
    wrap=false,
    units='samples'
  }
  local source_options = {"empty", "file"}
  for i=0,15,1 do
    local type_id = string.format("buftype%i", i)
    local file_id = string.format("buffile%i", i)
    local size_id = string.format("bufsize%i", i)
    params:add_option(type_id, string.format("source %i", i), source_options, 1)
    params:add_control(size_id, string.format("size %i", i), buf_size_spec, buf_size_format)
    params:add_file(file_id, string.format("file %i", i))
    params:hide(file_id)
    params:set_action(size_id, function(x) if params:visible(size_id) then engine.load_buffer(i, x, "") end end)
    params:set_action(file_id, function(x) if params:visible(file_id) and x ~= "" and x ~= "-" then engine.load_buffer(i, 0, x) end end)
    params:set_action(type_id, function (x)
      if source_options[x] == "empty" then
        params:show(size_id)
        params:hide(file_id)
      elseif source_options[x] == "file" then
        params:show(file_id)
        params:hide(size_id)
      end
      _menu.rebuild_params()
    end)
  end

  midi_device = {} -- container for connected midi devices
  midi_device_names = {}
  target = 1

  for i = 1,#midi.vports do -- query all ports
    midi_device[i] = midi.connect(i) -- connect each device
    local full_name =
    table.insert(midi_device_names,"port "..i..": "..util.trim_string_to_width(midi_device[i].name,40)) -- register its name
  end

  params:add_option("midi target", "midi target",midi_device_names,1)
  params:set_action("midi target", midi_target)
  params:add_binary("retrigger", "retrigger","toggle", 1)
  params:set_action("retrigger", function (x) retrigger = x end)
  params:add_number("bend_range", "bend range", 1, 48, 2)

  params:read(1)
  params:bang()
  sync_routine = clock.run(sync_every_beat)
end

function midi_target(x)
  midi_device[target].event = nil
  target = x
  midi_device[target].event = process_midi
end

music = require 'musicutil'

active_notes = {}

function set_pitch_bend(channel, bend_st)
  for n, val in pairs(active_notes) do
    if val then
      engine.note_bend(channel, n, music.note_num_to_freq(n + bend_st), bend_st)
    end
  end
  if note ~= nil then
    engine.set_param(19, music.note_num_to_freq(note + bend_st))
  end
end


function process_midi(data)
  local d = midi.to_msg(data)
  if d.type == "note_on" then
    -- global
    note = d.note
    active_notes[d.note] = true
    engine.note_on(d.ch, d.note, music.note_num_to_freq(d.note), d.vel/127)
    engine.set_param(21, d.vel / 127)
    engine.set_param(19, music.note_num_to_freq(d.note))
    if retrigger then
      engine.set_param(20, 0)
    end

    engine.set_param(20, 1) -- gate on
  elseif d.type == "note_off" then
    active_notes[d.note] = false
    engine.note_off(d.ch, d.note, music.note_num_to_freq(d.note))
    if  d.note == note then
      engine.set_param(20, 0) -- gate off
    end
  elseif d.type == "pitchbend" then
    local bend_st = (util.round(d.val / 2)) / 8192 * 2 -1 -- Convert to -1 to 1
    set_pitch_bend(d.ch, bend_st * params:get("bend_range"))
  end
end


function maybe_load_scene(full_filename)
  if loaded_scene == full_filename then
    return
  end
  f = io.open(full_filename, "r")
  if f == nil then
    err_line = "file not found"
    return
  end
  contents = f:read("*all")
  PTModel:load(editing_script, contents)
end

function sync_every_beat()
  while true do
    clock.sync(1)
    b = clock.get_beats()
    t = clock.get_tempo()
    -- print("Beat", b)
    engine.tempo_sync(b, t/60.0)
  end
end

function spaces_at_end(s)
  if s == nil then
    return 0
  end
  local m = string.match(s, " +$")
  if m == nil then
    return 0
  end
  return string.len(m)
end

function redraw()
  screen.clear()
  screen.level(16)
  screen.font_face(1) -- second best, and slightly more compact, face 25 size 6
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
  screen.move(8, 8*8-1)
  screen.text(editing)
  screen.level(1)
  screen.blend_mode(8)
  local up_to_cursor = string.sub(editing, 1, edit_col-1)
  screen.rect(screen.text_extents(up_to_cursor) + spaces_at_end(up_to_cursor) * 4 + 8, 56, 5, 8)
  screen.fill()
  screen.rect(0, 8*(edit_row-1), 128, 8)
  screen.fill()
  screen.update()
end

function keyboard.char(character)
  if model:super() then
    return
  end
  -- print("ADDING .", character, ".")
  editing = string.insert(editing, character:upper(), edit_col)
  edit_col = edit_col + 1
  redraw()
end

function delete_word_left()
  local index = word_index_left()
  editing = editing:sub(1, index-1)..editing:sub(edit_col, #editing)
  edit_col = index
end

function move_word_left()
  edit_col = word_index_left()
end

function word_index_left()
  local previous_index = 1
  local index = 0
  local end_index = 0

  while(true)
  do
    index, end_index = editing:find("[%w%p]+", end_index+1)
    if index == nil or index >= edit_col then
      return previous_index
    end
    previous_index = index
  end
end

function move_word_right()
  local index = editing:find("[%w%p]%s+", edit_col)
  edit_col = index == nil and #editing or index + 1
end

function edit_from_history(depth)
  if depth == 0 then
    editing = ""
    edit_col = 1
    return
  end
  for i, item in history:ipairs() do
    if i == history_depth then
      editing = item
      edit_col = string.len(editing) + 1
      break
    end
  end
end


function keyboard.code(key, value)
  -- print("KEY", key)
  -- print("POS", edit_col)
  screen.ping()
  moved_line = false
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
      elseif model:super() then
        move_word_left()
        return
      end
      edit_col = edit_col - 1
    elseif key == "RIGHT" then
      if editing:len() < edit_col then
        edit_col = editing:len() + 1
      elseif model:super() then
        move_word_right()
        return
      else
        edit_col = edit_col + 1
      end
    elseif key == "ENTER" and model:super() and editing_script == 10 then
      model:trigger_save()
    elseif key == "ENTER" then
      model:enter()
    elseif key == "UP" then
      if keyboard.shift() and history:count() > 0 then
        history_depth = (history_depth + 1) % (history:count() + 1)
        edit_from_history(history_depth)
      elseif edit_row == 1 then
        -- pass
      elseif model:super() and model:get(editing_script, edit_row) ~= "" then -- Added second condition to prevent weirdness when trying to shift empty lines
        local storedLine = model:get(editing_script, edit_row - 1)
        model:replace(editing_script, edit_row - 1, editing)
        model:replace(editing_script, edit_row, storedLine)
        moved_line = true
        model:to_line(edit_row - 1)
      else
        model:to_line(edit_row - 1)
      end
    elseif key == "DOWN" then
      if keyboard.shift() and history:count() > 0 then
        history_depth = (history_depth - 1) % (history:count() + 1)
        edit_from_history(history_depth)
      elseif edit_row > model:script_size(editing_script) then
        edit_row = model:script_size(editing_script) + 1
      else
        if model:super() and model:get(editing_script, edit_row + 1) ~= "" then
          local storedLine = model:get(editing_script, edit_row + 1)
          model:replace(editing_script, edit_row + 1, editing)
          model:replace(editing_script, edit_row, storedLine)
          moved_line = true
        end
        model:to_line(edit_row + 1)
      end
    elseif string.len(key) == 2 and string.sub(key, 1, 1) == "F" then
      local s = tonumber(string.sub(key, 2, 2))
      if s ~= nil then
        local current_script = editing_script
        editing_script = s
        if current_script ~= s then model:to_line(1) end --if we don't change scripts, don't change lines
      end
    elseif key == "ESC" then
      -- New script
      if model:super() and keyboard.shift() then
        edit_row = 1
        edit_col = 1
        model:load(editing_script, "")
      else
        editing_script = 10 -- this is the description
        edit_row = 1
        editing = model:get(editing_script, edit_row)
        edit_col = string.len(editing) + 1
      end
    elseif key == "C" and model:super() then
      clipboard = editing
    elseif key == "X" and model:super() then
      clipboard = editing
      editing = ""
      model:enter()
      model:to_line(edit_row - 1)
    elseif key == "V" and model:super() then
      editing = clipboard
      model:enter()
    elseif key == "W" and model:super() then
      delete_word_left()
    elseif key == "HOME" then
      edit_col = 1
    elseif key == "END" then
      edit_col = #editing
    else
      print("what's '"..key.."'?")
    end
  end
  print("Now editing row:", edit_row, "col:", edit_col)
  redraw()
end

function osc_in(path, args, from)
  print("osc", path, args[1], args[2], args[3], args[4], from)
  if path == "/report" then
    local nonce = args[1]
    local script_num = args[2] + 1
    err_line = args[3]
    local script_contents = args[4]

    if model.nonce == outstanding then
      PTModel.outstanding = 0
    end

    model.scripts[script_num] = split_lines(script_contents)

    -- If we just loaded a shorter script...
    if edit_row > model:script_size(editing_script) + 1 then
      edit_row = model:script_size(editing_script) + 1
      edit_col = 1
    end

    redraw()
  elseif path == "/save" then
    local text = args[1]
    local split_text = split_lines(text)
    local filename = (split_text[1] or "default") .. ".txt"
    local full_filename = _path.data .. "phonotype/" .. filename
    local f = io.open(full_filename,"w")
    if f == nil then
      err_line = "error writing file"
      return
    end
    f:write(text)
    f:close()
    loaded_scene = full_filename
    params:set("scene", full_filename)
    print("Wrote", full_filename)
    err_line = filename
  elseif path == "/crow/out" then
    local output = args[1]
    local slew = args[2]
    local volts = args[3]
    -- if crow_is_connected then
    print("crow output", output, slew, volts)
    crow.output[output].slew = slew
    crow.output[output].volts = volts
    -- end
  else
      print(path)
  end
end

osc.event = osc_in

function enc(n, d)
  if n == 2 then
    local increment = math.pow(math.sqrt(math.sqrt(2)), d)
    engine.fade_time(PTModel.nonce, editing_script-1, edit_row-1, increment)
  end
  if n == 3 then
    engine.quant(PTModel.nonce, editing_script-1, edit_row-1, d)
  end
end



function split_lines(str)
  local lines = {}
  for s in str:gmatch("[^\r\n]+") do
    table.insert(lines, s)
  end
  return lines
end
