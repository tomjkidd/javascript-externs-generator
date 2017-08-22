# javascript-externs-generator
Try it out: http://tomjkidd.github.io/javascript-externs-generator/

This project is a fork of [jmmk/javascript-externs-generator](https://github.com/jmmk/javascript-externs-generator).

## Motivation

I was attempting to update the react-datepicker to version 0.53.0, and while working out which dependencies I needed to get the DatePicker extern to generate, I found myself frustrated because I had to process 6 files, one-at-a-time, every time I wanted to test if the files I selected were compatible.

The jmmk/javascript-externs-generator does all the heavy lifting already, so I decided to use all of that existing code to actually perform the loading of js files and the creation of an extern.
But I wanted to do it using a simplified workflow that allows you to specify all of the urls at once.

## How to use it

### Web UI
* Go to https://tomjkidd.github.io/javascript-externs-generator/
* Enter a newline-separated list of the files you want to load, in dependency order.

```
https://cdnjs.cloudflare.com/ajax/libs/react/15.1.0/react.js
https://cdnjs.cloudflare.com/ajax/libs/react/15.1.0/react-dom.js
https://cdnjs.cloudflare.com/ajax/libs/react/15.1.0/react-with-addons.js
https://cdnjs.cloudflare.com/ajax/libs/moment.js/2.18.1/moment.js
https://rawgit.com/Pomax/react-onclickoutside/v5.7.1/index.js
https://cdnjs.cloudflare.com/ajax/libs/react-datepicker/0.53.0/react-datepicker.js
```

* Enter the main namespace to extern

```
DatePicker
```

* Use the `Extern!` button to generate the extern
* Use the `To Clipboard` button to copy the extern to your clipboard

See the [original readme](./orig-readme.md) for more information.
