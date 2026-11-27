#!/usr/bin/env python3
"""Makes the item pages of the guidebook: one page per creative tab, with every item, its picture and its description.

The item list comes from the game itself. Dump it with the development game test, then run this script:

    JAVA_TOOL_OPTIONS=-Deln.dumpGuide=/path/to/guide_items.json ./gradlew runGameTestServer
    python script/guide_pages.py /path/to/guide_items.json

Descriptions are the items' own tooltips; for the few items whose tooltip is only made on the client, a short one is
written here (EXTRA). Items with a hand-written page keep it: they are listed here too, with a link to it.
"""
import json
import os
import re
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'eln',
                    'guides', 'eln', 'guide')

PAGES = {
    'elnpowerelectronics': ('items-power.md', 'Power and cables', 'Cables, generators, transformers, batteries and switching.'),
    'elnmachines': ('items-machines.md', 'Machines', 'Machines that make, heat, mine and process.'),
    'elnsignalprocessing': ('items-signals.md', 'Signals and sensors', 'Signal cables, sensors, logic and control.'),
    'elnlighting': ('items-lighting.md', 'Lighting', 'Lamps, sockets and their supplies.'),
    'elntoolsarmor': ('items-tools.md', 'Tools and armor', 'Meters, brushes and other tools.'),
    'elnoresmaterials': ('items-materials.md', 'Ores and materials', 'Ores, dusts, ingots and parts for crafting.'),
    'elncreative': ('items-creative.md', 'Creative', 'Parts for creative mode and testing.'),
}

# For items whose description is only made on the client.
EXTRA = {
    'Multimeter': 'Use it on any part to read its voltage, current and power.',
    'Thermometer': 'Use it on any part to read its temperature.',
    'Universal-Meter': 'A multimeter and a thermometer in one.',
    'Wireless Analyzer': 'Shows the wireless signal channels around you.',
    'Config Copy Tool': 'Copies the settings of a part (sneak-use) and pastes them onto another of the same kind.',
    'Energy Meter': 'Measures the energy and power going through it, between its two sides.',
    'Advanced Energy Meter': 'An energy meter with more readings and a time base.',
    'MQTT Energy Meter': 'An energy meter that publishes its readings over MQTT.',
    'Energy Exporter': 'Gives Electrical Age power to the machines of other mods, as FE.',
    'Overhead Lines': 'Lines strung between poles; see the grid page.',
    'Grid DC-DC Converter': 'Joins 51.2 kV transmission lines to 12.8 kV distribution lines, like a distribution substation without breakers.',
    'Utility Pole': 'A T1 distribution pole: 12.8 kV lines, 20 A. Link poles with high voltage cable.',
    'Utility Pole w/DC-DC Converter': 'A T1 pole with a transformer: takes 12.8 kV lines down to 3.2 kV for a cable at its foot.',
    'Transmission Tower': 'A T2 transmission tower: 51.2 kV lines, 40 A, up to 96 blocks apart.',
    'Direct Utility Pole': 'A T1 pole that joins its line directly to a cable at its foot, at the line voltage.',
    'Grid Switch': 'Opens and closes an overhead line.',
    'Joint': 'Joins shafts, to carry rotation between machines.',
    'Joint hub': 'Joins up to four shafts.',
    'Flywheel': 'Stores energy in its spin and smooths the speed of a shaft.',
    'Tachometer': 'Gives the speed of a shaft as a signal.',
    'Clutch': 'Joins or splits two shafts, by signal.',
    'Fixed Shaft': 'A shaft that does not turn: holds the shafts joined to it still.',
    'Rolling Shaft Machine': 'Makes shafts.',
    'Eln Computer Probe': 'Lets a computer read and write signals.',
    'On/off regulator 1%': 'Switches its output on and off to keep its input near a set point, within 1 %.',
    'On/off regulator 10%': 'Switches its output on and off to keep its input near a set point, within 10 %.',
    'Analogic Regulator': 'A regulator with a smooth output.',
    'MQTT Signal Controller': 'Exchanges signals with an MQTT server.',
    'Street Light': 'A tall lamp post for streets.',
    'Ore Scanner': 'Finds ores around it.',
    'Mining Pipe': 'Pipe for the autominer to dig down with.',
    'Machine Booster': 'An upgrade that makes a machine faster.',
    'Player Filter': 'An entity sensor filter: only players.',
    'Monster Filter': 'An entity sensor filter: only monsters.',
    'Animal Filter': 'An entity sensor filter: only animals.',
    '50V Egg Incubator': 'Hatches eggs, powered at 50 V.',
    'Electric Minecart': 'A minecart that runs on power from overhead lines.',
    'Hot Water Bucket': 'Hot water, from a heat exchanger.',
    'Cold Water Bucket': 'Cold water.',
}

BRUSH = 'Paints cables. A painted cable only joins cables of its color, and the line of its color on a power bundle.'


def describe(item):
    lines = [l.rstrip() for l in item['tooltip'] if l.strip()]
    if not lines:
        if item['name'].endswith(' Brush'):
            return [BRUSH]
        extra = EXTRA.get(item['name'])
        return [extra] if extra else []
    return lines


def escape(text):
    return re.sub(r'([\\`*_<>\[\]])', r'\\\1', text)


def handwritten():
    """The items that have a hand-written page, and that page."""
    pages = {}
    for f in os.listdir(ROOT):
        if not f.endswith('.md') or f.startswith('items'):
            continue
        text = open(os.path.join(ROOT, f), encoding='utf-8').read()
        for m in re.finditer(r'^  - (eln:[a-z0-9_]+)$', text, re.M):
            pages[m.group(1)] = f
    return pages


def main(path):
    tabs = json.load(open(path, encoding='utf-8'))
    own = handwritten()
    position = 20
    index = []
    for tab in tabs:
        if tab['tab'] not in PAGES:
            continue
        file, title, intro = PAGES[tab['tab']]
        ids = [i['id'] for i in tab['items'] if i['id'] not in own]
        out = ['---', 'navigation:', '  title: ' + title, '  parent: items.md', '  position: %d' % position]
        if ids:
            out.append('item_ids:')
            out += ['  - ' + i for i in ids]
        out += ['---', '', '# ' + title, '', intro, '']
        for item in tab['items']:
            out.append('## ' + escape(item['name']))
            out.append('')
            out.append('<ItemImage id="%s" />' % item['id'])
            out.append('')
            for line in describe(item):
                text = escape(line.strip())
                out.append(('- ' + text) if line.startswith('  ') else text + '  ')
            if item['id'] in own:
                out.append('')
                out.append('More: [%s](%s).' % (own[item['id']][:-3].replace('-', ' '), own[item['id']]))
            out.append('')
        open(os.path.join(ROOT, file), 'w', encoding='utf-8', newline='\n').write('\n'.join(out))
        index.append('- [%s](%s): %s' % (title, file, intro[0].lower() + intro[1:]))
        position += 1
    out = ['---', 'navigation:', '  title: All items', '  position: 20', '---', '', '# All items', '',
           'Every item of the mod, by creative tab, with its picture and description.', ''] + index + ['']
    open(os.path.join(ROOT, 'items.md'), 'w', encoding='utf-8', newline='\n').write('\n'.join(out))
    print('wrote %d pages' % (len(index) + 1))


if __name__ == '__main__':
    main(sys.argv[1])
