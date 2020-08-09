import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { View } from 'react-native';

import NativeVlcPlayer from './src/NativeVlcPlayer';

class VlcPlayer extends Component {
  static propTypes = {
    style: PropTypes.shape({
      width : PropTypes.number.isRequired,
      height: PropTypes.number.isRequired
    })
  }

  static defaultProps = {
    style: {
      width : 0,
      height: 0
    }
  }

  render() {
    const { forwardRef, style, ...rest  } = this.props;
    const props = JSON.parse(JSON.stringify(rest));
    return (
      <View
        style={{
          position: 'relative'
        }}>
        <NativeVlcPlayer
          {...props}
          ref={forwardRef}
          style={style}
        />
      </View>
    );
  }
}

export default React.forwardRef((props, ref) => (
  <VlcPlayer {...props} forwardRef={ref}/>
));
